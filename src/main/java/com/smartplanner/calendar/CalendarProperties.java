package com.smartplanner.calendar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки синхронизации календарей (раздел app.calendar в application.yml).
 *
 * @param syncIntervalMs как часто перекачивать календари
 * @param daysBehind     сколько дней прошлого хранить (чтобы видеть прошедшую неделю)
 * @param daysAhead      на сколько дней вперёд разворачивать повторения
 * @param maxSizeMb      максимальный размер .ics файла
 * @param maxPerUser     сколько подписок может быть у одного пользователя
 */
@ConfigurationProperties(prefix = "app.calendar")
public record CalendarProperties(
        @DefaultValue("1800000") long syncIntervalMs,
        @DefaultValue("7") int daysBehind,
        @DefaultValue("60") int daysAhead,
        @DefaultValue("5") int maxSizeMb,
        @DefaultValue("10") int maxPerUser
) {
}
