package com.smartplanner.calendar;

import com.smartplanner.calendar.ics.IcsException;
import com.smartplanner.user.AppUser;
import com.smartplanner.webapp.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** REST API подписок на календари и событий из них (для текущего пользователя Mini App). */
@RestController
@RequestMapping("/api/me")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    public record SubscribeRequest(@NotBlank @Size(max = 2000) String url, @Size(max = 200) String name) {
    }

    public record UpdateRequest(@Size(max = 200) String name, Boolean remindersEnabled) {
    }

    public record SubscriptionResponse(Long id, String name, String url, boolean remindersEnabled,
                                       Instant lastSyncedAt, String lastError) {
        static SubscriptionResponse from(CalendarSubscription s) {
            return new SubscriptionResponse(s.getId(), s.getName(), s.getUrl(), s.isRemindersEnabled(),
                    s.getLastSyncedAt(), s.getLastError());
        }
    }

    /** Событие в часовом поясе пользователя. */
    public record EventResponse(Long id, Long calendarId, String calendarName, String title,
                                String location, String description,
                                LocalDateTime startAt, LocalDateTime endAt, boolean allDay) {
        static EventResponse from(CalendarEvent e, ZoneId zone) {
            return new EventResponse(e.getId(), e.getSubscription().getId(), e.getSubscription().getName(),
                    e.getTitle(), e.getLocation(), e.getDescription(),
                    LocalDateTime.ofInstant(e.getStartAt(), zone), LocalDateTime.ofInstant(e.getEndAt(), zone),
                    e.isAllDay());
        }
    }

    @GetMapping("/calendars")
    public List<SubscriptionResponse> list(@CurrentUser AppUser user) {
        return calendarService.list(user.getId()).stream().map(SubscriptionResponse::from).toList();
    }

    @PostMapping("/calendars")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@CurrentUser AppUser user, @Valid @RequestBody SubscribeRequest request)
            throws IcsException {
        return SubscriptionResponse.from(calendarService.subscribe(user.getId(), request.url(), request.name()).subscription());
    }

    @PatchMapping("/calendars/{calendarId}")
    public SubscriptionResponse update(@CurrentUser AppUser user, @PathVariable Long calendarId,
                                       @Valid @RequestBody UpdateRequest request) {
        return SubscriptionResponse.from(
                calendarService.update(user.getId(), calendarId, request.name(), request.remindersEnabled()));
    }

    @PostMapping("/calendars/{calendarId}/sync")
    public CalendarSyncService.SyncResult sync(@CurrentUser AppUser user, @PathVariable Long calendarId) {
        return calendarService.syncNow(user.getId(), calendarId);
    }

    @DeleteMapping("/calendars/{calendarId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@CurrentUser AppUser user, @PathVariable Long calendarId) {
        calendarService.unsubscribe(user.getId(), calendarId);
    }

    /** События за период: GET /api/me/events?from=2026-10-26&to=2026-11-01 (обе даты включительно). */
    @GetMapping("/events")
    public List<EventResponse> events(@CurrentUser AppUser user,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ZoneId zone = user.zoneId();
        if (to.isBefore(from) || to.isAfter(from.plusDays(62))) {
            throw new IllegalArgumentException("Период должен быть от 1 до 62 дней");
        }
        return calendarService.findEvents(user.getId(),
                        from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().map(e -> EventResponse.from(e, zone)).toList();
    }
}
