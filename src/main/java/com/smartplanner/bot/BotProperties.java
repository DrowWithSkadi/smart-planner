package com.smartplanner.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки бота из application.yml (раздел telegram.bot).
 *
 * @param token     токен от @BotFather. Берётся из переменной окружения TELEGRAM_BOT_TOKEN,
 *                  в репозиторий его класть нельзя
 * @param username  имя бота без @
 * @param webAppUrl адрес Mini App (https). Пока пустой — кнопка «Открыть планер» не показывается
 */
@ConfigurationProperties(prefix = "telegram.bot")
public record BotProperties(String token, String username, String webAppUrl) {

    public boolean hasWebApp() {
        return webAppUrl != null && !webAppUrl.isBlank();
    }
}
