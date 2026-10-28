package com.smartplanner.bot;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuickAddParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void textWithoutTime() {
        var task = QuickAddParser.parse("Купить хлеб", TODAY);
        assertEquals("Купить хлеб", task.title());
        assertNull(task.dueAt());
    }

    @Test
    void timeMeansToday() {
        var task = QuickAddParser.parse("18:00 Тренировка", TODAY);
        assertEquals("Тренировка", task.title());
        assertEquals(LocalDateTime.of(2026, 10, 28, 18, 0), task.dueAt());
    }

    @Test
    void tomorrowWithTime() {
        var task = QuickAddParser.parse("завтра 9:30 Пара по Java", TODAY);
        assertEquals("Пара по Java", task.title());
        assertEquals(LocalDateTime.of(2026, 10, 29, 9, 30), task.dueAt());
    }

    @Test
    void relativeDayWithoutTimeUsesDefaultTime() {
        var task = QuickAddParser.parse("Послезавтра позвонить маме", TODAY);
        assertEquals("позвонить маме", task.title());
        assertEquals(LocalDateTime.of(2026, 10, 30, 9, 0), task.dueAt());
    }

    @Test
    void dateAndTime() {
        var task = QuickAddParser.parse("05.11 14:15 Встреча", TODAY);
        assertEquals(LocalDateTime.of(2026, 11, 5, 14, 15), task.dueAt());
    }

    @Test
    void pastDateWithoutYearMovesToNextYear() {
        var task = QuickAddParser.parse("10.01 Сдать сессию", TODAY);
        assertEquals(LocalDateTime.of(2027, 1, 10, 9, 0), task.dueAt());
    }

    @Test
    void fullDate() {
        var task = QuickAddParser.parse("01.12.2026 Сдать проект", TODAY);
        assertEquals("Сдать проект", task.title());
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), task.dueAt());
    }

    @Test
    void invalidDateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> QuickAddParser.parse("31.02 Что-то", TODAY));
    }

    @Test
    void invalidTimeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> QuickAddParser.parse("25:00 Что-то", TODAY));
    }

    @Test
    void emptyTextIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> QuickAddParser.parse("   ", TODAY));
    }
}
