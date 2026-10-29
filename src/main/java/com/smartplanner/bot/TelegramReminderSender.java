package com.smartplanner.bot;

import com.smartplanner.reminder.Reminder;
import com.smartplanner.reminder.ReminderSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Отправляет напоминания в Telegram. У задач есть кнопки «Готово» и «Отложить». */
@Component
@ConditionalOnBotToken
public class TelegramReminderSender implements ReminderSender {

    private final TelegramClient telegram;

    public TelegramReminderSender(TelegramClient telegram) {
        this.telegram = telegram;
    }

    @Override
    public void send(Reminder reminder) throws Exception {
        SendMessage message = SendMessage.builder()
                .chatId(reminder.user().getChatId())
                .text(BotMessages.reminder(reminder, reminder.user().zoneId()))
                .build();
        if (reminder.source() == Reminder.Source.TASK) {
            message.setReplyMarkup(BotMessages.reminderButtons(reminder.refId()));
        }
        try {
            telegram.execute(message);
        } catch (TelegramApiException e) {
            throw new Exception("Telegram не принял напоминание " + reminder.source() + " " + reminder.refId(), e);
        }
    }
}
