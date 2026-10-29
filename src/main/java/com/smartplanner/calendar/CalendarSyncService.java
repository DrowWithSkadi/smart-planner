package com.smartplanner.calendar;

import com.smartplanner.calendar.ics.IcsException;
import com.smartplanner.calendar.ics.IcsFetcher;
import com.smartplanner.calendar.ics.IcsParser;
import com.smartplanner.calendar.ics.ParsedCalendar;
import com.smartplanner.calendar.ics.ParsedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Скачивает календари и обновляет события в базе.
 *
 * <p>Скачивание идёт вне транзакции (оно может быть долгим), а запись в базу — внутри неё.
 * События сопоставляются по паре (UID, время начала): изменённые обновляются,
 * новые добавляются, пропавшие из календаря удаляются.</p>
 */
@Service
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);

    private final CalendarSubscriptionRepository subscriptionRepository;
    private final CalendarEventRepository eventRepository;
    private final CalendarProperties properties;
    private final TransactionTemplate transaction;
    private final IcsFetcher fetcher;
    private final IcsParser parser = new IcsParser();
    private final int remindBeforeMinutes;

    public CalendarSyncService(CalendarSubscriptionRepository subscriptionRepository,
                               CalendarEventRepository eventRepository,
                               CalendarProperties properties,
                               TransactionTemplate transaction,
                               @Value("${app.reminders.default-before-minutes:15}") int remindBeforeMinutes) {
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.transaction = transaction;
        this.remindBeforeMinutes = remindBeforeMinutes;
        this.fetcher = new IcsFetcher((long) properties.maxSizeMb() * 1024 * 1024);
    }

    /**
     * Итог синхронизации — для логов и ответа пользователю.
     *
     * @param error текст ошибки или null, если всё прошло хорошо
     */
    public record SyncResult(int added, int updated, int removed, boolean notModified, String error) {

        static SyncResult failed(String error) {
            return new SyncResult(0, 0, 0, false, error);
        }

        public boolean ok() {
            return error == null;
        }
    }

    /** Скачанный и разобранный календарь — ещё не записан в базу. */
    public record Download(IcsFetcher.Result http, ParsedCalendar calendar) {
    }

    /** Скачивает и разбирает календарь. Бросает IcsException с понятным текстом ошибки. */
    public Download download(String url, String etag, String lastModified, ZoneId zone) throws IcsException {
        URI uri = IcsFetcher.normalize(url);
        IcsFetcher.Result http = fetcher.fetch(uri, etag, lastModified);
        if (http.notModified()) {
            return new Download(http, null);
        }
        Instant now = Instant.now();
        ParsedCalendar calendar = parser.parse(http.body(), zone,
                now.minus(Duration.ofDays(properties.daysBehind())),
                now.plus(Duration.ofDays(properties.daysAhead())));
        return new Download(http, calendar);
    }

    /** Синхронизирует одну подписку. Ошибку записывает в подписку, а не бросает. */
    public SyncResult sync(Long subscriptionId) {
        CalendarSubscription snapshot = transaction.execute(status ->
                subscriptionRepository.findById(subscriptionId).map(s -> {
                    s.getUser().getTimezone(); // подгружаем пользователя, пока открыта сессия
                    return s;
                }).orElse(null));
        if (snapshot == null) {
            return SyncResult.failed("Календарь не найден");
        }

        // Если календарь давно не синхронизировался, качаем целиком, а не «только изменения»:
        // иначе повторения не развернутся на новые дни вперёд
        boolean stale = snapshot.getLastSyncedAt() == null
                || snapshot.getLastSyncedAt().isBefore(Instant.now().minus(Duration.ofHours(12)));
        try {
            Download download = download(snapshot.getUrl(),
                    stale ? null : snapshot.getEtag(),
                    stale ? null : snapshot.getLastModified(),
                    snapshot.getUser().zoneId());
            return transaction.execute(status -> {
                CalendarSubscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
                if (subscription == null) {
                    return SyncResult.failed("Календарь удалён"); // подписку удалили, пока качали
                }
                return apply(subscription, download);
            });
        } catch (IcsException e) {
            log.warn("Календарь {} не синхронизирован: {}", subscriptionId, e.getMessage());
            transaction.executeWithoutResult(status ->
                    subscriptionRepository.findById(subscriptionId).ifPresent(s -> s.markFailed(e.getMessage())));
            return SyncResult.failed(e.getMessage());
        }
    }

    /** Записывает скачанные события в базу. Вызывать внутри транзакции. */
    SyncResult apply(CalendarSubscription subscription, Download download) {
        if (download.http().notModified()) {
            subscription.markSynced(download.http().etag(), download.http().lastModified());
            return new SyncResult(0, 0, 0, true, null);
        }

        Map<String, CalendarEvent> existing = new HashMap<>();
        for (CalendarEvent event : eventRepository.findBySubscriptionId(subscription.getId())) {
            existing.put(key(event.getUid(), event.getStartAt()), event);
        }

        int added = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        List<ParsedEvent> parsed = download.calendar().events();
        for (ParsedEvent p : parsed) {
            String key = key(p.uid(), p.startAt());
            seen.add(key);
            CalendarEvent event = existing.get(key);
            if (event == null) {
                event = new CalendarEvent(subscription, p.uid(), p.startAt());
                added++;
            } else {
                updated++;
            }
            Instant remindAt = p.allDay() ? null : p.startAt().minus(Duration.ofMinutes(remindBeforeMinutes));
            event.update(p.title(), p.location(), p.description(), p.endAt(), p.allDay(), remindAt);
            eventRepository.save(event);
        }

        int removed = 0;
        for (Map.Entry<String, CalendarEvent> entry : existing.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                eventRepository.delete(entry.getValue());
                removed++;
            }
        }

        subscription.markSynced(download.http().etag(), download.http().lastModified());
        log.info("Календарь «{}» синхронизирован: +{} ~{} -{}", subscription.getName(), added, updated, removed);
        return new SyncResult(added, updated, removed, false, null);
    }

    /** Фоновая синхронизация всех календарей. Первый запуск — через минуту после старта. */
    @Scheduled(initialDelay = 60_000, fixedDelayString = "${app.calendar.sync-interval-ms:1800000}")
    public void syncAll() {
        List<Long> ids = transaction.execute(status ->
                subscriptionRepository.findAll().stream().map(CalendarSubscription::getId).toList());
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            try {
                sync(id);
            } catch (RuntimeException e) {
                log.error("Ошибка при синхронизации календаря {}", id, e);
            }
        }
    }

    private static String key(String uid, Instant start) {
        return uid + "|" + start.toEpochMilli();
    }
}
