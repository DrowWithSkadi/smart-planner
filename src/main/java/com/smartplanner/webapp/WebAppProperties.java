package com.smartplanner.webapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки Mini App (раздел app.webapp).
 *
 * @param initDataMaxAgeHours сколько часов действительны данные авторизации от Telegram
 * @param devTelegramId       режим разработки: если задан, запросы без данных Telegram выполняются
 *                            от имени этого пользователя. Нужен, чтобы открыть интерфейс в обычном
 *                            браузере на localhost. На сервере не включать!
 */
@ConfigurationProperties(prefix = "app.webapp")
public record WebAppProperties(
        @DefaultValue("24") int initDataMaxAgeHours,
        Long devTelegramId
) {
}
