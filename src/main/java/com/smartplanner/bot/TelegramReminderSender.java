package com.smartplanner.bot;

import com.smartplanner.reminder.ReminderSender;
import com.smartplanner.task.Task;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Отправляет напоминания в Telegram с кнопками «Готово» и «Отложить». */
@Component
@ConditionalOnBotToken
public class TelegramReminderSender implements ReminderSender {

    private final TelegramClient telegram;

    public TelegramReminderSender(TelegramClient telegram) {
        this.telegram = telegram;
    }

    @Override
    public void send(Task task) throws Exception {
        SendMessage message = SendMessage.builder()
                .chatId(task.getUser().getChatId())
                .text(BotMessages.reminder(task, task.getUser().zoneId()))
                .replyMarkup(BotMessages.reminderButtons(task.getId()))
                .build();
        try {
            telegram.execute(message);
        } catch (TelegramApiException e) {
            throw new Exception("Telegram не принял напоминание для задачи " + task.getId(), e);
        }
    }
}
