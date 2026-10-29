package com.smartplanner.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Данные для создания или изменения события (задачи).
 * Время — местное время пользователя, например "2026-10-29T18:00".
 *
 * @param dueAt начало
 * @param endAt конец (необязательно, но не раньше начала)
 */
public record TaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        LocalDateTime dueAt,
        LocalDateTime endAt,
        @Min(0) @Max(10080) Integer remindBeforeMinutes
) {
}
