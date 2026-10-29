package com.smartplanner.reminder;

/**
 * Способ доставки напоминаний. Сейчас это Telegram (TelegramReminderSender),
 * но можно добавить и другие — например, email или push-уведомления.
 */
public interface ReminderSender {

    void send(Reminder reminder) throws Exception;
}
