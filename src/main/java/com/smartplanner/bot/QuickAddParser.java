package com.smartplanner.bot;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбирает быстрый ввод задачи обычным текстом. Поддерживаемые форматы:
 * <pre>
 *   Купить хлеб                     — без времени
 *   18:00 Тренировка                — сегодня в 18:00
 *   завтра 9:30 Пара по Java        — завтра в 9:30
 *   послезавтра Позвонить маме      — послезавтра в 09:00
 *   29.10 18:00 Встреча             — 29 октября в 18:00
 *   05.11.2026 Сдать проект         — 5 ноября 2026 в 09:00
 * </pre>
 * Позже сюда можно подключить нейросеть для разбора свободного текста.
 */
public final class QuickAddParser {

    /** Время по умолчанию, если указана только дата. */
    static final LocalTime DEFAULT_TIME = LocalTime.of(9, 0);

    private static final Pattern PATTERN = Pattern.compile(
            "^(?:(?<rel>сегодня|завтра|послезавтра)\\s+|(?<date>\\d{1,2}\\.\\d{1,2}(?:\\.\\d{4})?)\\s+)?"
                    + "(?:(?<time>\\d{1,2}:\\d{2})\\s+)?"
                    + "(?<title>\\S.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    private QuickAddParser() {
    }

    public record ParsedTask(String title, LocalDateTime dueAt) {
    }

    /**
     * @param text  текст от пользователя
     * @param today сегодняшняя дата в часовом поясе пользователя
     * @throws IllegalArgumentException если текст пустой или дата/время некорректны
     */
    public static ParsedTask parse(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Напиши, что нужно сделать");
        }
        Matcher m = PATTERN.matcher(text.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Не понял формат задачи");
        }

        String title = m.group("title").trim();
        LocalDate date = parseDate(m.group("rel"), m.group("date"), today);
        LocalTime time = parseTime(m.group("time"));

        if (date == null && time == null) {
            return new ParsedTask(title, null);
        }
        if (date == null) {
            date = today;
        }
        if (time == null) {
            time = DEFAULT_TIME;
        }
        return new ParsedTask(title, LocalDateTime.of(date, time));
    }

    private static LocalDate parseDate(String relative, String date, LocalDate today) {
        if (relative != null) {
            return switch (relative.toLowerCase(Locale.ROOT)) {
                case "завтра" -> today.plusDays(1);
                case "послезавтра" -> today.plusDays(2);
                default -> today;
            };
        }
        if (date == null) {
            return null;
        }
        String[] parts = date.split("\\.");
        try {
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            if (parts.length == 3) {
                return LocalDate.of(Integer.parseInt(parts[2]), month, day);
            }
            LocalDate result = LocalDate.of(today.getYear(), month, day);
            // "05.01" в декабре — это январь следующего года
            return result.isBefore(today) ? result.plusYears(1) : result;
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Такой даты не существует: " + date);
        }
    }

    private static LocalTime parseTime(String time) {
        if (time == null) {
            return null;
        }
        String[] parts = time.split(":");
        try {
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Некорректное время: " + time);
        }
    }
}
