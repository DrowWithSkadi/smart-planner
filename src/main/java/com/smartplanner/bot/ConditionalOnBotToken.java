package com.smartplanner.bot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Создаёт бин только если токен бота задан.
 * Без токена приложение тоже запускается — работает REST API, а бот просто выключен.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnExpression("!'${telegram.bot.token:}'.isBlank()")
public @interface ConditionalOnBotToken {
}
