package com.smartplanner.calendar.ics;

import java.time.Instant;

/**
 * Одно конкретное событие из календаря. Повторяющееся событие («каждый вторник»)
 * уже развёрнуто: на каждое повторение — свой ParsedEvent со своим временем.
 *
 * @param uid    UID события из файла. У всех повторений одной серии он одинаковый,
 *               поэтому уникальность определяет пара (uid, startAt)
 * @param allDay событие на весь день (без конкретного времени)
 */
public record ParsedEvent(
        String uid,
        String title,
        String location,
        String description,
        Instant startAt,
        Instant endAt,
        boolean allDay
) {
}
