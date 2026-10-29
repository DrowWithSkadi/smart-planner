package com.smartplanner.calendar.ics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IcsParserTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
    private static final Instant FROM = at("2026-10-26T00:00");
    private static final Instant TO = at("2026-11-23T00:00");

    private final IcsParser parser = new IcsParser();

    private static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(TALLINN).toInstant();
    }

    private static String calendar(String... lines) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//test//EN\r\nX-WR-CALNAME:Расписание\r\n"
                + String.join("\r\n", lines) + "\r\nEND:VCALENDAR\r\n";
    }

    private ParsedCalendar parse(String ics) throws IcsException {
        return parser.parse(ics, TALLINN, FROM, TO);
    }

    @Test
    void readsCalendarNameAndSimpleUtcEvent() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:simple-1",
                "DTSTAMP:20261001T000000Z",
                "DTSTART:20261028T080000Z",
                "DTEND:20261028T093000Z",
                "SUMMARY:Базы данных",
                "LOCATION:Аудитория 204",
                "END:VEVENT"));

        assertEquals("Расписание", result.name());
        assertEquals(1, result.events().size());
        ParsedEvent e = result.events().get(0);
        assertEquals("simple-1", e.uid());
        assertEquals("Базы данных", e.title());
        assertEquals("Аудитория 204", e.location());
        assertEquals(Instant.parse("2026-10-28T08:00:00Z"), e.startAt());
        assertEquals(Instant.parse("2026-10-28T09:30:00Z"), e.endAt());
        assertFalse(e.allDay());
    }

    @Test
    void eventWithTimezone() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:tz-1",
                "DTSTAMP:20261001T000000Z",
                "DTSTART;TZID=Europe/Tallinn:20261029T100000",
                "DTEND;TZID=Europe/Tallinn:20261029T113000",
                "SUMMARY:Java",
                "END:VEVENT"));

        ParsedEvent e = result.events().get(0);
        assertEquals(at("2026-10-29T10:00"), e.startAt());
        assertEquals(at("2026-10-29T11:30"), e.endAt());
    }

    @Test
    void floatingTimeUsesUserZone() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:floating-1",
                "DTSTAMP:20261001T000000Z",
                "DTSTART:20261030T120000",
                "DTEND:20261030T130000",
                "SUMMARY:Обед",
                "END:VEVENT"));

        assertEquals(at("2026-10-30T12:00"), result.events().get(0).startAt());
    }

    @Test
    void allDayEvent() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:allday-1",
                "DTSTAMP:20261001T000000Z",
                "DTSTART;VALUE=DATE:20261101",
                "DTEND;VALUE=DATE:20261102",
                "SUMMARY:Каникулы",
                "END:VEVENT"));

        ParsedEvent e = result.events().get(0);
        assertTrue(e.allDay());
        assertEquals(at("2026-11-01T00:00"), e.startAt());
        assertEquals(at("2026-11-02T00:00"), e.endAt());
    }

    @Test
    void weeklyRecurrenceWithExceptionDate() throws IcsException {
        // каждый вторник в 10:00 с 3 ноября, 4 раза, кроме 10 ноября
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:weekly-1",
                "DTSTAMP:20261001T000000Z",
                "DTSTART;TZID=Europe/Tallinn:20261103T100000",
                "DTEND;TZID=Europe/Tallinn:20261103T113000",
                "RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=TU",
                "EXDATE;TZID=Europe/Tallinn:20261110T100000",
                "SUMMARY:Пара по Java",
                "END:VEVENT"));

        List<Instant> starts = result.events().stream().map(ParsedEvent::startAt).toList();
        // 3, 17 ноября; 24 ноября не входит в диапазон, 10 ноября исключено
        assertEquals(List.of(at("2026-11-03T10:00"), at("2026-11-17T10:00")), starts);
        assertEquals(at("2026-11-03T11:30"), result.events().get(0).endAt());
    }

    @Test
    void recurrenceStartedBeforeRangeIsIncluded() throws IcsException {
        // серия началась в сентябре, но повторения в нашем диапазоне должны появиться
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:weekly-old",
                "DTSTAMP:20261001T000000Z",
                "DTSTART;TZID=Europe/Tallinn:20260901T090000",
                "DTEND;TZID=Europe/Tallinn:20260901T100000",
                "RRULE:FREQ=WEEKLY;BYDAY=TU",
                "SUMMARY:Физика",
                "END:VEVENT"));

        assertEquals(4, result.events().size()); // 27.10, 3.11, 10.11, 17.11
        assertEquals(at("2026-10-27T09:00"), result.events().get(0).startAt());
    }

    @Test
    void movedOccurrenceReplacesOriginal() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:moved-1",
                "DTSTAMP:20261001T000000Z",
                "DTSTART;TZID=Europe/Tallinn:20261102T100000",
                "DTEND;TZID=Europe/Tallinn:20261102T110000",
                "RRULE:FREQ=WEEKLY;COUNT=2",
                "SUMMARY:Математика",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "UID:moved-1",
                "DTSTAMP:20261001T000000Z",
                "RECURRENCE-ID;TZID=Europe/Tallinn:20261109T100000",
                "DTSTART;TZID=Europe/Tallinn:20261110T140000",
                "DTEND;TZID=Europe/Tallinn:20261110T150000",
                "SUMMARY:Математика (перенос)",
                "LOCATION:Аудитория 305",
                "END:VEVENT"));

        assertEquals(2, result.events().size());
        assertEquals(at("2026-11-02T10:00"), result.events().get(0).startAt());
        ParsedEvent moved = result.events().get(1);
        assertEquals(at("2026-11-10T14:00"), moved.startAt());
        assertEquals("Математика (перенос)", moved.title());
        assertEquals("Аудитория 305", moved.location());
    }

    @Test
    void cancelledOccurrenceIsRemoved() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:cancel-1",
                "DTSTAMP:20261001T000000Z",
                "DTSTART:20261102T080000Z",
                "DTEND:20261102T090000Z",
                "RRULE:FREQ=DAILY;COUNT=3",
                "SUMMARY:Зарядка",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "UID:cancel-1",
                "DTSTAMP:20261001T000000Z",
                "RECURRENCE-ID:20261103T080000Z",
                "DTSTART:20261103T080000Z",
                "DTEND:20261103T090000Z",
                "STATUS:CANCELLED",
                "SUMMARY:Зарядка",
                "END:VEVENT"));

        List<Instant> starts = result.events().stream().map(ParsedEvent::startAt).toList();
        assertEquals(List.of(Instant.parse("2026-11-02T08:00:00Z"), Instant.parse("2026-11-04T08:00:00Z")), starts);
    }

    @Test
    void cancelledEventIsSkipped() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:cancelled",
                "DTSTAMP:20261001T000000Z",
                "DTSTART:20261102T080000Z",
                "STATUS:CANCELLED",
                "SUMMARY:Отменено",
                "END:VEVENT"));

        assertTrue(result.events().isEmpty());
    }

    @Test
    void eventsOutsideRangeAreIgnored() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:old",
                "DTSTAMP:20261001T000000Z",
                "DTSTART:20260101T080000Z",
                "DTEND:20260101T090000Z",
                "SUMMARY:Давно",
                "END:VEVENT"));

        assertTrue(result.events().isEmpty());
    }

    @Test
    void eventWithoutSummaryGetsPlaceholder() throws IcsException {
        ParsedCalendar result = parse(calendar(
                "BEGIN:VEVENT",
                "UID:nosummary",
                "DTSTAMP:20261001T000000Z",
                "DTSTART:20261102T080000Z",
                "END:VEVENT"));

        ParsedEvent e = result.events().get(0);
        assertEquals("(без названия)", e.title());
        assertNull(e.location());
        assertEquals(e.startAt(), e.endAt());
    }

    @Test
    void notACalendarIsRejected() {
        assertThrows(IcsException.class, () -> parse("<html>Not found</html>"));
    }
}
