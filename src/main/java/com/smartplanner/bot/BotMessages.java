package com.smartplanner.bot;

import com.smartplanner.task.Task;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Тексты и кнопки, которые отправляет бот. Собраны в одном месте, чтобы их было легко менять. */
final class BotMessages {

    static final String CB_DONE = "done";
    static final String CB_SNOOZE = "snooze";
    static final String CB_DELETE = "del";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private BotMessages() {
    }

    static String welcome(String firstName) {
        String name = firstName == null || firstName.isBlank() ? "" : ", " + firstName;
        return "Привет" + name + "! 👋\n\n"
                + "Я Smart Planner: записываю задачи и напоминаю о них.\n\n"
                + help();
    }

    static String help() {
        return """
                Как добавить задачу — просто напиши её:
                • Купить хлеб
                • 18:00 Тренировка
                • завтра 9:30 Пара по Java
                • 29.10 18:00 Встреча

                Команды:
                /today — задачи на сегодня
                /list — ближайшие задачи
                /help — эта подсказка

                Напоминание придёт за 15 минут до задачи.""";
    }

    static String taskCreated(Task task, ZoneId zone) {
        StringBuilder sb = new StringBuilder("✅ Записал: ").append(task.getTitle());
        if (task.getDueAt() != null) {
            sb.append("\n🕒 ").append(formatDue(task.getDueAt(), zone));
            if (task.getRemindAt() != null && task.getRemindAt().isAfter(Instant.now())) {
                sb.append("\n🔔 Напомню в ").append(TIME.format(task.getRemindAt().atZone(zone)));
            }
        }
        return sb.toString();
    }

    static String taskList(String header, List<Task> tasks, ZoneId zone, String emptyText) {
        if (tasks.isEmpty()) {
            return emptyText;
        }
        StringBuilder sb = new StringBuilder(header).append("\n");
        for (Task t : tasks) {
            sb.append("\n").append(t.isDone() ? "✅ " : "▫️ ");
            if (t.getDueAt() != null) {
                sb.append(formatDue(t.getDueAt(), zone)).append(" — ");
            }
            sb.append(t.getTitle());
        }
        return sb.toString();
    }

    static String reminder(Task task, ZoneId zone) {
        StringBuilder sb = new StringBuilder("⏰ Напоминание\n\n").append(task.getTitle());
        if (task.getDueAt() != null) {
            sb.append("\n🕒 ").append(TIME.format(task.getDueAt().atZone(zone)));
            long minutes = Duration.between(Instant.now(), task.getDueAt()).toMinutes();
            if (minutes > 0) {
                sb.append(" (через ").append(minutes).append(" мин)");
            }
        }
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            sb.append("\n\n").append(task.getDescription());
        }
        return sb.toString();
    }

    static InlineKeyboardMarkup reminderButtons(Long taskId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("✅ Готово", CB_DONE + ":" + taskId),
                        button("⏰ +15 мин", CB_SNOOZE + ":" + taskId + ":15"),
                        button("⏰ +1 час", CB_SNOOZE + ":" + taskId + ":60")))
                .build();
    }

    static InlineKeyboardMarkup createdButtons(Long taskId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("✅ Готово", CB_DONE + ":" + taskId),
                        button("🗑 Удалить", CB_DELETE + ":" + taskId)))
                .build();
    }

    /** "сегодня 18:00", "завтра 09:30" или "29.10 18:00". */
    private static String formatDue(Instant due, ZoneId zone) {
        ZonedDateTime dt = due.atZone(zone);
        LocalDate today = LocalDate.now(zone);
        if (dt.toLocalDate().equals(today)) {
            return "сегодня " + TIME.format(dt);
        }
        if (dt.toLocalDate().equals(today.plusDays(1))) {
            return "завтра " + TIME.format(dt);
        }
        return DATE_TIME.format(dt);
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }
}
