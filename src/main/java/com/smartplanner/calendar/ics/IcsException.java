package com.smartplanner.calendar.ics;

/** Не удалось скачать или разобрать календарь. Сообщение можно показывать пользователю. */
public class IcsException extends Exception {

    public IcsException(String message) {
        super(message);
    }

    public IcsException(String message, Throwable cause) {
        super(message, cause);
    }
}
