package com.smartplanner.reminder;

import com.smartplanner.calendar.CalendarEvent;
import com.smartplanner.task.Task;
import com.smartplanner.user.AppUser;

import java.time.Instant;

/**
 * Напоминание, готовое к отправке. Одинаково выглядит для задач и для событий календаря,
 * поэтому способам доставки (Telegram и т.д.) не важно, откуда оно взялось.
 *
 * @param source откуда напоминание: от задачи (можно отметить «готово» и отложить) или из календаря
 * @param refId  ID задачи или события
 */
public record Reminder(
        Source source,
        Long refId,
        AppUser user,
        String title,
        Instant startAt,
        String location,
        String description,
        String calendarName
) {
    public enum Source { TASK, CALENDAR_EVENT }

    public static Reminder of(Task task) {
        return new Reminder(Source.TASK, task.getId(), task.getUser(), task.getTitle(), task.getDueAt(),
                null, task.getDescription(), null);
    }

    public static Reminder of(CalendarEvent event) {
        return new Reminder(Source.CALENDAR_EVENT, event.getId(), event.getSubscription().getUser(),
                event.getTitle(), event.getStartAt(), event.getLocation(), event.getDescription(),
                event.getSubscription().getName());
    }
}
