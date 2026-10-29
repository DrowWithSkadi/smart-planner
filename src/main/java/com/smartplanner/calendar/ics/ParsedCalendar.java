package com.smartplanner.calendar.ics;

import java.util.List;

/**
 * Результат разбора .ics файла.
 *
 * @param name   название календаря из файла (X-WR-CALNAME), может быть null
 * @param events события в запрошенном диапазоне, отсортированы по времени начала
 */
public record ParsedCalendar(String name, List<ParsedEvent> events) {
}
