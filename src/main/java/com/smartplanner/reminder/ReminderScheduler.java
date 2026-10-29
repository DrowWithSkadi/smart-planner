package com.smartplanner.reminder;

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
 * Каждые 30 секунд проверяет, по каким задачам пора отправить напоминание.
 * Если ни одного способа доставки нет (например, не задан токен бота), просто ничего не делает.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final TaskRepository taskRepository;
    private final List<ReminderSender> senders;

    public ReminderScheduler(TaskRepository taskRepository, ObjectProvider<ReminderSender> senderProvider) {
        this.taskRepository = taskRepository;
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
        List<Task> due = taskRepository.findDueReminders(Instant.now());
        for (Task task : due) {
            for (ReminderSender sender : senders) {
                try {
                    sender.send(task);
                } catch (Exception e) {
                    log.error("Не удалось отправить напоминание по задаче {}", task.getId(), e);
                }
            }
            // помечаем в любом случае, чтобы не слать одно и то же бесконечно
            task.setReminderSent(true);
        }
        if (!due.isEmpty()) {
            log.info("Отправлено напоминаний: {}", due.size());
        }
    }
}
