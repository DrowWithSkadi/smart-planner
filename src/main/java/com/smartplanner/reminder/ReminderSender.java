package com.smartplanner.reminder;

import com.smartplanner.task.Task;

/**
 * Способ доставки напоминаний. Сейчас это Telegram (TelegramReminderSender),
 * но можно добавить и другие — например, email или push-уведомления.
 */
public interface ReminderSender {

    void send(Task task) throws Exception;
}
