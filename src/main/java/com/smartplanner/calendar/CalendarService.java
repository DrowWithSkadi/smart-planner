package com.smartplanner.calendar;

import com.smartplanner.calendar.ics.IcsException;
import com.smartplanner.calendar.ics.IcsFetcher;
import com.smartplanner.common.NotFoundException;
import com.smartplanner.user.AppUser;
import com.smartplanner.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Подписки на календари: добавление, удаление, просмотр событий. Используют и бот, и REST API. */
@Service
public class CalendarService {

    private final CalendarSubscriptionRepository subscriptionRepository;
    private final CalendarEventRepository eventRepository;
    private final CalendarSyncService syncService;
    private final CalendarProperties properties;
    private final UserService userService;
    private final TransactionTemplate transaction;

    public CalendarService(CalendarSubscriptionRepository subscriptionRepository,
                           CalendarEventRepository eventRepository,
                           CalendarSyncService syncService,
                           CalendarProperties properties,
                           UserService userService,
                           TransactionTemplate transaction) {
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
        this.syncService = syncService;
        this.properties = properties;
        this.userService = userService;
        this.transaction = transaction;
    }

    /** Результат подписки: сама подписка и сколько событий загрузилось. */
    public record SubscribeResult(CalendarSubscription subscription, int eventCount) {
    }

    /**
     * Подписывает пользователя на календарь. Сначала скачивает его — так сразу видно,
     * что ссылка рабочая, и события появляются без ожидания фоновой синхронизации.
     *
     * @param name название; если не указано — берётся из самого календаря
     */
    public SubscribeResult subscribe(Long userId, String url, String name) throws IcsException {
        AppUser user = userService.get(userId);
        URI uri = IcsFetcher.normalize(url);
        String normalizedUrl = uri.toString();

        if (subscriptionRepository.countByUserId(userId) >= properties.maxPerUser()) {
            throw new IcsException("Можно подписаться не больше чем на " + properties.maxPerUser() + " календарей");
        }
        if (subscriptionRepository.existsByUserIdAndUrl(userId, normalizedUrl)) {
            throw new IcsException("Ты уже подписан на этот календарь");
        }

        CalendarSyncService.Download download = syncService.download(normalizedUrl, null, null, user.zoneId());
        String finalName = chooseName(name, download.calendar().name(), uri);

        return transaction.execute(status -> {
            CalendarSubscription subscription = subscriptionRepository.save(
                    new CalendarSubscription(user, finalName, normalizedUrl));
            CalendarSyncService.SyncResult result = syncService.apply(subscription, download);
            return new SubscribeResult(subscription, result.added());
        });
    }

    @Transactional(readOnly = true)
    public List<CalendarSubscription> list(Long userId) {
        return subscriptionRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional(readOnly = true)
    public CalendarSubscription get(Long userId, Long subscriptionId) {
        return subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new NotFoundException("Календарь " + subscriptionId + " не найден"));
    }

    @Transactional
    public void unsubscribe(Long userId, Long subscriptionId) {
        CalendarSubscription subscription = get(userId, subscriptionId);
        eventRepository.deleteBySubscriptionId(subscription.getId());
        subscriptionRepository.delete(subscription);
    }

    @Transactional
    public CalendarSubscription update(Long userId, Long subscriptionId, String name, Boolean remindersEnabled) {
        CalendarSubscription subscription = get(userId, subscriptionId);
        if (name != null && !name.isBlank()) {
            subscription.setName(name.trim());
        }
        if (remindersEnabled != null) {
            subscription.setRemindersEnabled(remindersEnabled);
        }
        return subscription;
    }

    /** Синхронизировать прямо сейчас, не дожидаясь расписания. */
    public CalendarSyncService.SyncResult syncNow(Long userId, Long subscriptionId) {
        get(userId, subscriptionId); // проверяем, что календарь принадлежит пользователю
        return syncService.sync(subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> findEvents(Long userId, Instant from, Instant to) {
        return eventRepository.findForUser(userId, from, to);
    }

    /** События на конкретный день (по часовому поясу пользователя). */
    @Transactional(readOnly = true)
    public List<CalendarEvent> findForDay(Long userId, LocalDate day) {
        ZoneId zone = userService.get(userId).zoneId();
        return findEvents(userId, day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static String chooseName(String requested, String fromCalendar, URI uri) {
        String name = requested != null && !requested.isBlank() ? requested.trim()
                : fromCalendar != null ? fromCalendar
                : uri.getHost();
        return name.length() > 200 ? name.substring(0, 200) : name;
    }
}
