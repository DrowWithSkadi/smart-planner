package com.smartplanner.bot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramConfig {

    /** Клиент для отправки сообщений в Telegram (используют бот и напоминания). */
    @Bean
    @ConditionalOnBotToken
    public TelegramClient telegramClient(BotProperties properties) {
        return new OkHttpTelegramClient(properties.token());
    }
}
