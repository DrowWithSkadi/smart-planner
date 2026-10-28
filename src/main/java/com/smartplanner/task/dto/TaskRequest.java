package com.smartplanner.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Данные для создания или изменения задачи.
 * dueAt — местное время пользователя, например "2026-10-29T18:00".
 */
public record TaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        LocalDateTime dueAt,
        @Min(0) @Max(10080) Integer remindBeforeMinutes
) {
}
