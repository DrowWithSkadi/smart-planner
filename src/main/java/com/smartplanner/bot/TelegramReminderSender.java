package com.smartplanner.bot;

import com.smartplanner.reminder.Reminder;
import com.smartplanner.reminder.ReminderSender;
import com.smartplanner.storage.ImageStorage;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Отправляет напоминания в Telegram. У задач есть кнопки «Готово» и «Отложить».
 * Если к событию прикреплена картинка — напоминание приходит фотографией с подписью.
 */
@Component
@ConditionalOnBotToken
public class TelegramReminderSender implements ReminderSender {

    /** Telegram ограничивает подпись к фото 1024 символами. */
    private static final int MAX_CAPTION = 1024;

    private final TelegramClient telegram;
    private final ImageStorage imageStorage;

    public TelegramReminderSender(TelegramClient telegram, ImageStorage imageStorage) {
        this.telegram = telegram;
        this.imageStorage = imageStorage;
    }

    @Override
    public void send(Reminder reminder) throws Exception {
        String text = BotMessages.reminder(reminder, reminder.user().zoneId());
        InlineKeyboardMarkup buttons = reminder.source() == Reminder.Source.TASK
                ? BotMessages.reminderButtons(reminder.refId()) : null;
        Optional<Path> image = imageStorage.resolve(reminder.imageName());
        try {
            if (image.isPresent()) {
                SendPhoto photo = SendPhoto.builder()
                        .chatId(reminder.user().getChatId())
                        .photo(new InputFile(image.get().toFile()))
                        .caption(text.length() > MAX_CAPTION ? text.substring(0, MAX_CAPTION - 1) + "…" : text)
                        .build();
                photo.setReplyMarkup(buttons);
                telegram.execute(photo);
            } else {
                SendMessage message = SendMessage.builder()
                        .chatId(reminder.user().getChatId())
                        .text(text)
                        .build();
                message.setReplyMarkup(buttons);
                telegram.execute(message);
            }
        } catch (TelegramApiException e) {
            throw new Exception("Telegram не принял напоминание " + reminder.source() + " " + reminder.refId(), e);
        }
    }
}
