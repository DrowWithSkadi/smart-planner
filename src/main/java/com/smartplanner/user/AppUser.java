package com.smartplanner.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Пользователь планера. Создаётся автоматически, когда человек пишет боту /start.
 * Таблица называется app_user, потому что "user" — зарезервированное слово во многих БД.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID пользователя в Telegram — по нему узнаём человека. */
    @Column(nullable = false, unique = true)
    private Long telegramId;

    /** ID чата с ботом — сюда отправляются напоминания. */
    @Column(nullable = false)
    private Long chatId;

    private String firstName;

    private String username;

    /** Часовой пояс пользователя, например "Europe/Tallinn". */
    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
        // нужен для JPA
    }

    public AppUser(Long telegramId, Long chatId, String firstName, String username, String timezone) {
        this.telegramId = telegramId;
        this.chatId = chatId;
        this.firstName = firstName;
        this.username = username;
        this.timezone = timezone;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public Long getId() {
        return id;
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
