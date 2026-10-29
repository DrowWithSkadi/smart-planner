package com.smartplanner.task.dto;

import com.smartplanner.task.Task;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Событие (задача) в том виде, в котором его отдаёт API: время уже в часовом поясе пользователя. */
public record TaskResponse(
        Long id,
        String title,
        String description,
        LocalDateTime dueAt,
        LocalDateTime endAt,
        Integer remindBeforeMinutes,
        boolean done,
        String imageUrl
) {
    public static TaskResponse from(Task task, ZoneId zone) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
                local(task.getDueAt(), zone), local(task.getEndAt(), zone),
                task.getRemindBeforeMinutes(), task.isDone(),
                task.getImageName() == null ? null : "/images/" + task.getImageName());
    }

    private static LocalDateTime local(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }
}
