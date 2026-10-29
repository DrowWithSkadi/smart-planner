package com.smartplanner.bot;

import com.smartplanner.calendar.CalendarEvent;
import com.smartplanner.calendar.CalendarSubscription;
import com.smartplanner.reminder.Reminder;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Тексты и кнопки, которые отправляет бот. Собраны в одном месте, чтобы их было легко менять. */
final class BotMessages {

    static final String CB_DONE = "done";
    static final String CB_SNOOZE = "snooze";
    static final String CB_DELETE = "del";
    static final String CB_UNSUBSCRIBE = "unsub";
    static final String CB_SYNC = "sync";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private BotMessages() {
    }

    static String welcome(String firstName) {
        String name = firstName == null || firstName.isBlank() ? "" : ", " + firstName;
        return "Привет" + name + "! 👋\n\n"
                + "Я Smart Planner: записываю задачи, подтягиваю расписание из календарей и напоминаю о делах.\n\n"
                + help();
    }

    static String help() {
        return """
                Как добавить задачу — просто напиши её:
                • Купить хлеб
                • 18:00 Тренировка
                • завтра 9:30 Пара по Java
                • 29.10 18:00 Встреча

                Чтобы подписаться на календарь (расписание, Google, iCloud), пришли ссылку на него — \
                webcal://… или https://….ics

                Команды:
                /today — план на сегодня
                /tomorrow — план на завтра
                /list — ближайшие задачи
                /calendars — мои календари
                /help — эта подсказка

                Напоминание придёт за 15 минут до задачи или события.""";
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

    /** План на день: события календарей и задачи вместе, по времени. */
    static String agenda(String header, List<Task> tasks, List<CalendarEvent> events, ZoneId zone, String emptyText) {
        if (tasks.isEmpty() && events.isEmpty()) {
            return emptyText;
        }
        record Line(Instant at, boolean allDay, String text) {
        }
        List<Line> lines = new ArrayList<>();
        for (CalendarEvent e : events) {
            StringBuilder sb = new StringBuilder("📅 ");
            sb.append(e.isAllDay() ? "весь день" : TIME.format(e.getStartAt().atZone(zone))
                    + "–" + TIME.format(e.getEndAt().atZone(zone)));
            sb.append(" — ").append(e.getTitle());
            if (e.getLocation() != null) {
                sb.append(" (").append(e.getLocation()).append(")");
            }
            lines.add(new Line(e.getStartAt(), e.isAllDay(), sb.toString()));
        }
        for (Task t : tasks) {
            String time = t.getDueAt() == null ? ""
                    : TIME.format(t.getDueAt().atZone(zone))
                    + (t.getEndAt() == null ? "" : "–" + TIME.format(t.getEndAt().atZone(zone))) + " — ";
            lines.add(new Line(t.getDueAt() == null ? Instant.MAX : t.getDueAt(), false,
                    (t.isDone() ? "✅ " : "▫️ ") + time + t.getTitle()));
        }
        // сначала события на весь день, потом всё остальное по времени
        lines.sort(Comparator.comparing(Line::allDay).reversed().thenComparing(Line::at));

        StringBuilder sb = new StringBuilder(header).append("\n");
        lines.forEach(l -> sb.append("\n").append(l.text()));
        return sb.toString();
    }

    static String reminder(Reminder reminder, ZoneId zone) {
        String icon = reminder.source() == Reminder.Source.CALENDAR_EVENT ? "📅" : "⏰";
        StringBuilder sb = new StringBuilder(icon).append(" Напоминание\n\n").append(reminder.title());
        if (reminder.startAt() != null) {
            sb.append("\n🕒 ").append(TIME.format(reminder.startAt().atZone(zone)));
            long minutes = Duration.between(Instant.now(), reminder.startAt()).toMinutes();
            if (minutes > 0) {
                sb.append(" (через ").append(minutes).append(" мин)");
            }
        }
        if (reminder.location() != null) {
            sb.append("\n📍 ").append(reminder.location());
        }
        if (reminder.calendarName() != null) {
            sb.append("\n🗂 ").append(reminder.calendarName());
        }
        if (reminder.description() != null && !reminder.description().isBlank()) {
            String text = reminder.description();
            sb.append("\n\n").append(text.length() > 500 ? text.substring(0, 500) + "…" : text);
        }
        return sb.toString();
    }

    static String calendarSubscribed(CalendarSubscription subscription, int eventCount) {
        return "📅 Подписался на календарь «" + subscription.getName() + "»\n"
                + "Загружено событий: " + eventCount + "\n\n"
                + "Календарь будет обновляться сам каждые 30 минут. "
                + "О событиях напомню за 15 минут. Посмотреть план — /today";
    }

    static String calendarList(List<CalendarSubscription> subscriptions, ZoneId zone) {
        if (subscriptions.isEmpty()) {
            return "Календарей пока нет.\n\nПришли ссылку на календарь (webcal://… или https://….ics), "
                    + "и я буду подтягивать из него события.";
        }
        StringBuilder sb = new StringBuilder("🗂 Твои календари:\n");
        int i = 1;
        for (CalendarSubscription s : subscriptions) {
            sb.append("\n").append(i++).append(". ").append(s.getName());
            if (s.getLastError() != null) {
                sb.append("\n   ⚠️ ").append(s.getLastError());
            } else if (s.getLastSyncedAt() != null) {
                sb.append("\n   обновлён ").append(DATE_TIME.format(s.getLastSyncedAt().atZone(zone)));
            }
        }
        return sb.toString();
    }

    static InlineKeyboardMarkup calendarButtons(List<CalendarSubscription> subscriptions) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder<?, ?> builder = InlineKeyboardMarkup.builder();
        int i = 1;
        for (CalendarSubscription s : subscriptions) {
            builder.keyboardRow(new InlineKeyboardRow(
                    button("🔄 " + i, CB_SYNC + ":" + s.getId()),
                    button("🗑 Отписаться от " + i, CB_UNSUBSCRIBE + ":" + s.getId())));
            i++;
        }
        return builder.build();
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
