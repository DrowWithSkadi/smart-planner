package com.smartplanner.task.dto;

import com.smartplanner.task.Task;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Задача в том виде, в котором её отдаёт API: время уже в часовом поясе пользователя. */
public record TaskResponse(
        Long id,
        String title,
        String description,
        LocalDateTime dueAt,
        Integer remindBeforeMinutes,
        boolean done
) {
    public static TaskResponse from(Task task, ZoneId zone) {
        LocalDateTime due = task.getDueAt() == null ? null : LocalDateTime.ofInstant(task.getDueAt(), zone);
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
                due, task.getRemindBeforeMinutes(), task.isDone());
    }
}
