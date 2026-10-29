package com.smartplanner.calendar;

import com.smartplanner.calendar.ics.IcsException;
import com.smartplanner.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
import java.util.Map;

/** REST API подписок на календари и событий из них. */
@RestController
@RequestMapping("/api/users/{userId}")
public class CalendarController {

    private final CalendarService calendarService;
    private final UserService userService;

    public CalendarController(CalendarService calendarService, UserService userService) {
        this.calendarService = calendarService;
        this.userService = userService;
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
    public List<SubscriptionResponse> list(@PathVariable Long userId) {
        return calendarService.list(userId).stream().map(SubscriptionResponse::from).toList();
    }

    @PostMapping("/calendars")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@PathVariable Long userId, @Valid @RequestBody SubscribeRequest request)
            throws IcsException {
        return SubscriptionResponse.from(calendarService.subscribe(userId, request.url(), request.name()).subscription());
    }

    @PatchMapping("/calendars/{calendarId}")
    public SubscriptionResponse update(@PathVariable Long userId, @PathVariable Long calendarId,
                                       @Valid @RequestBody UpdateRequest request) {
        return SubscriptionResponse.from(
                calendarService.update(userId, calendarId, request.name(), request.remindersEnabled()));
    }

    @PostMapping("/calendars/{calendarId}/sync")
    public CalendarSyncService.SyncResult sync(@PathVariable Long userId, @PathVariable Long calendarId) {
        return calendarService.syncNow(userId, calendarId);
    }

    @DeleteMapping("/calendars/{calendarId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable Long userId, @PathVariable Long calendarId) {
        calendarService.unsubscribe(userId, calendarId);
    }

    /** События за период: GET /api/users/1/events?from=2026-10-26&to=2026-11-01 (обе даты включительно). */
    @GetMapping("/events")
    public List<EventResponse> events(@PathVariable Long userId,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ZoneId zone = userService.get(userId).zoneId();
        if (to.isBefore(from) || to.isAfter(from.plusDays(62))) {
            throw new IllegalArgumentException("Период должен быть от 1 до 62 дней");
        }
        return calendarService.findEvents(userId,
                        from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().map(e -> EventResponse.from(e, zone)).toList();
    }

    /** Ошибки ссылки или календаря отдаём как 400 с понятным текстом. */
    @ExceptionHandler({IcsException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
