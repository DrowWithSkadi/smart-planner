package com.smartplanner.calendar;

import com.smartplanner.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Подписка пользователя на внешний календарь по ссылке (.ics / webcal). */
@Entity
@Table(name = "calendar_subscription")
public class CalendarSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2000)
    private String url;

    /** Напоминать о событиях этого календаря. */
    private boolean remindersEnabled = true;

    /** Время последней успешной синхронизации. */
    private Instant lastSyncedAt;

    /** Текст последней ошибки синхронизации (null — всё хорошо). */
    @Column(length = 500)
    private String lastError;

    /** Метки версии от сервера — чтобы не качать календарь, если он не менялся. */
    @Column(length = 500)
    private String etag;

    @Column(length = 100)
    private String lastModified;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected CalendarSubscription() {
        // нужен для JPA
    }

    public CalendarSubscription(AppUser user, String name, String url) {
        this.user = user;
        this.name = name;
        this.url = url;
    }

    /** Отмечает успешную синхронизацию. */
    public void markSynced(String etag, String lastModified) {
        this.lastSyncedAt = Instant.now();
        this.lastError = null;
        this.etag = etag;
        this.lastModified = lastModified;
    }

    public void markFailed(String error) {
        this.lastError = error == null ? "Неизвестная ошибка"
                : error.length() > 500 ? error.substring(0, 500) : error;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public boolean isRemindersEnabled() {
        return remindersEnabled;
    }

    public void setRemindersEnabled(boolean remindersEnabled) {
        this.remindersEnabled = remindersEnabled;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getEtag() {
        return etag;
    }

    public String getLastModified() {
        return lastModified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
