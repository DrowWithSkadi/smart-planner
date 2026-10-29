package com.smartplanner.reminder;

import com.smartplanner.calendar.CalendarEvent;
import com.smartplanner.calendar.CalendarEventRepository;
import com.smartplanner.task.Task;
import com.smartplanner.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Каждые 30 секунд проверяет, по каким задачам и событиям календаря пора отправить напоминание.
 * Если ни одного способа доставки нет (например, не задан токен бота), просто ничего не делает.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final TaskRepository taskRepository;
    private final CalendarEventRepository eventRepository;
    private final List<ReminderSender> senders;

    public ReminderScheduler(TaskRepository taskRepository,
                             CalendarEventRepository eventRepository,
                             ObjectProvider<ReminderSender> senderProvider) {
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        // ObjectProvider, а не List: если бот выключен, отправителей нет — и это не ошибка
        this.senders = senderProvider.orderedStream().toList();
        if (senders.isEmpty()) {
            log.warn("Токен бота не задан — напоминания отправляться не будут. Смотри README, раздел «Запуск».");
        }
    }

    @Scheduled(fixedDelayString = "${app.reminders.check-interval-ms:30000}")
    @Transactional
    public void sendDueReminders() {
        if (senders.isEmpty()) {
            return;
        }
        Instant now = Instant.now();

        List<Task> tasks = taskRepository.findDueReminders(now);
        for (Task task : tasks) {
            deliver(Reminder.of(task));
            // помечаем в любом случае, чтобы не слать одно и то же бесконечно
            task.setReminderSent(true);
        }

        List<CalendarEvent> events = eventRepository.findDueReminders(now);
        for (CalendarEvent event : events) {
            deliver(Reminder.of(event));
            event.setReminderSent(true);
        }

        if (!tasks.isEmpty() || !events.isEmpty()) {
            log.info("Отправлено напоминаний: задачи — {}, события — {}", tasks.size(), events.size());
        }
    }

    private void deliver(Reminder reminder) {
        for (ReminderSender sender : senders) {
            try {
                sender.send(reminder);
            } catch (Exception e) {
                log.error("Не удалось отправить напоминание {} {}", reminder.source(), reminder.refId(), e);
            }
        }
    }
}
