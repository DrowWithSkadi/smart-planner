package com.smartplanner.calendar.ics;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.Duration;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Разбирает календарь в формате iCalendar (.ics) и превращает его в список конкретных событий.
 *
 * <p>Что учитывается:</p>
 * <ul>
 *   <li>повторения (RRULE, RDATE) и исключения из них (EXDATE);</li>
 *   <li>изменённые отдельные повторения (событие с RECURRENCE-ID — например, пару перенесли в другую аудиторию);</li>
 *   <li>отменённые события (STATUS:CANCELLED);</li>
 *   <li>время в UTC, с часовым поясом (TZID), «плавающее» время и события на весь день.</li>
 * </ul>
 *
 * Класс не зависит от Spring и базы данных — его удобно тестировать отдельно.
 */
public class IcsParser {

    /** Защита от «бесконечных» правил: больше стольких повторений одной серии не разворачиваем. */
    private static final int MAX_OCCURRENCES_PER_SERIES = 1000;

    static final int MAX_TITLE = 500;
    static final int MAX_LOCATION = 500;
    static final int MAX_DESCRIPTION = 4000;

    /**
     * @param ics  содержимое файла
     * @param zone часовой пояс пользователя — для «плавающего» времени и событий на весь день
     * @param from начало интересующего диапазона
     * @param to   конец диапазона
     */
    public ParsedCalendar parse(String ics, ZoneId zone, Instant from, Instant to) throws IcsException {
        Calendar calendar = build(ics);
        String name = calendar.getProperty("X-WR-CALNAME").map(Property::getValue).map(String::trim)
                .filter(s -> !s.isEmpty()).orElse(null);

        // Группируем события по UID: основное событие серии + его изменённые повторения
        Map<String, Series> seriesByUid = new LinkedHashMap<>();
        int index = 0;
        for (VEvent event : calendar.<VEvent>getComponents(Component.VEVENT)) {
            String uid = event.getUid().map(Property::getValue).filter(s -> !s.isBlank())
                    .orElse("no-uid-" + index);
            index++;
            Series series = seriesByUid.computeIfAbsent(uid, k -> new Series());
            if (event.getProperty(Property.RECURRENCE_ID).isPresent()) {
                series.overrides.add(event);
            } else if (series.master == null) {
                series.master = event;
            }
        }

        // Ключ uid|начало — защищает от дублей, если одно и то же повторение пришло дважды
        Map<String, ParsedEvent> result = new LinkedHashMap<>();
        for (Map.Entry<String, Series> entry : seriesByUid.entrySet()) {
            for (ParsedEvent e : expand(entry.getKey(), entry.getValue(), zone, from, to)) {
                if (e.endAt().isAfter(from) && e.startAt().isBefore(to)) {
                    result.putIfAbsent(e.uid() + "|" + e.startAt(), e);
                }
            }
        }

        List<ParsedEvent> events = new ArrayList<>(result.values());
        events.sort(Comparator.comparing(ParsedEvent::startAt).thenComparing(ParsedEvent::title));
        return new ParsedCalendar(name, events);
    }

    private Calendar build(String ics) throws IcsException {
        if (ics == null || !ics.contains("BEGIN:VCALENDAR")) {
            throw new IcsException("Это не календарь: в ответе нет данных в формате iCalendar");
        }
        try {
            return new CalendarBuilder().build(new StringReader(ics));
        } catch (IOException | ParserException e) {
            throw new IcsException("Не удалось разобрать календарь: " + e.getMessage(), e);
        }
    }

    private List<ParsedEvent> expand(String uid, Series series, ZoneId zone, Instant from, Instant to) {
        List<ParsedEvent> occurrences = new ArrayList<>();

        if (series.master != null && !isCancelled(series.master)) {
            VEvent master = series.master;
            if (isRecurring(master)) {
                Period<ZonedDateTime> window = new Period<>(from.atZone(zone), to.atZone(zone));
                Set<Period<Temporal>> periods = master.calculateRecurrenceSet(window);
                periods.stream().limit(MAX_OCCURRENCES_PER_SERIES).forEach(p ->
                        occurrences.add(toEvent(uid, master, p.getStart(), p.getEnd(), zone)));
            } else {
                single(uid, master, zone).ifPresent(occurrences::add);
            }
        }

        // Изменённые повторения: убираем исходное и добавляем новое (если его не отменили)
        for (VEvent override : series.overrides) {
            Optional<DateProperty<Temporal>> recurrenceId = override.getProperty(Property.RECURRENCE_ID);
            if (recurrenceId.isEmpty() || recurrenceId.get().getDate() == null) {
                continue;
            }
            Instant originalStart = toInstant(recurrenceId.get().getDate(), zone);
            occurrences.removeIf(e -> e.startAt().equals(originalStart));
            if (!isCancelled(override)) {
                single(uid, override, zone).ifPresent(occurrences::add);
            }
        }
        return occurrences;
    }

    /** Обычное (неповторяющееся) событие или одно изменённое повторение. */
    private Optional<ParsedEvent> single(String uid, VEvent event, ZoneId zone) {
        Optional<? extends DateProperty<Temporal>> start = event.getStartDate();
        if (start.isEmpty() || start.get().getDate() == null) {
            return Optional.empty();
        }
        Temporal startValue = start.get().getDate();
        Temporal endValue = event.<Temporal>getEndDate(false).map(DateProperty::getDate).orElse(null);
        if (endValue == null) {
            // Нет DTEND — смотрим на DURATION. Если нет и его, toEvent выберет конец сам
            // (getEndDate(true) не подходит: он считает такое событие длиной в сутки)
            Optional<Duration> duration = event.getProperty(Property.DURATION);
            if (duration.isPresent()) {
                endValue = startValue.plus(duration.get().getDuration());
            }
        }
        return Optional.of(toEvent(uid, event, startValue, endValue, zone));
    }

    private ParsedEvent toEvent(String uid, VEvent event, Temporal start, Temporal end, ZoneId zone) {
        boolean allDay = start instanceof LocalDate;
        Instant startAt = toInstant(start, zone);
        Instant endAt = end == null ? null : toInstant(end, zone);
        if (endAt == null || endAt.isBefore(startAt)) {
            // нет конца: для события на весь день — сутки, иначе — момент
            endAt = allDay ? toInstant(((LocalDate) start).plusDays(1), zone) : startAt;
        }
        String title = text(event.getSummary(), MAX_TITLE);
        return new ParsedEvent(uid,
                title == null ? "(без названия)" : title,
                text(event.getLocation(), MAX_LOCATION),
                text(event.getDescription(), MAX_DESCRIPTION),
                startAt, endAt, allDay);
    }

    /** Переводит любое время из календаря в Instant (момент времени в UTC). */
    static Instant toInstant(Temporal temporal, ZoneId zone) {
        if (temporal instanceof Instant instant) {
            return instant;
        }
        if (temporal instanceof ZonedDateTime zoned) {
            return zoned.toInstant();
        }
        if (temporal instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (temporal instanceof LocalDateTime local) {
            return local.atZone(zone).toInstant(); // «плавающее» время — в поясе пользователя
        }
        if (temporal instanceof LocalDate date) {
            return date.atStartOfDay(zone).toInstant();
        }
        throw new IllegalArgumentException("Неизвестный тип даты: " + temporal.getClass());
    }

    private static boolean isRecurring(VEvent event) {
        return event.getProperty(Property.RRULE).isPresent() || event.getProperty(Property.RDATE).isPresent();
    }

    private static boolean isCancelled(VEvent event) {
        return event.getStatus() != null && "CANCELLED".equalsIgnoreCase(event.getStatus().getValue());
    }

    private static String text(Property property, int maxLength) {
        if (property == null || property.getValue() == null) {
            return null;
        }
        String value = property.getValue().trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength - 1) + "…" : value;
    }

    /** Основное событие серии и его изменённые повторения. */
    private static final class Series {
        VEvent master;
        final List<VEvent> overrides = new ArrayList<>();
    }
}
