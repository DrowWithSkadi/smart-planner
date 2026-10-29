package com.smartplanner.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Событие, загруженное из внешнего календаря. Только для чтения: при следующей
 * синхронизации данные перезапишутся из источника.
 * Повторяющиеся события хранятся развёрнутыми — одна строка на каждое повторение.
 */
@Entity
@Table(name = "calendar_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_occurrence",
                columnNames = {"subscription_id", "uid", "startAt"}),
        indexes = {
                @Index(name = "idx_event_start", columnList = "startAt"),
                @Index(name = "idx_event_remind_at", columnList = "remindAt")
        })
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id")
    private CalendarSubscription subscription;

    @Column(nullable = false, length = 1000)
    private String uid;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String location;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    private boolean allDay;

    /** Когда напомнить. null — не напоминать (например, событие на весь день). */
    private Instant remindAt;

    private boolean reminderSent;

    protected CalendarEvent() {
        // нужен для JPA
    }

    public CalendarEvent(CalendarSubscription subscription, String uid, Instant startAt) {
        this.subscription = subscription;
        this.uid = uid;
        this.startAt = startAt;
    }

    /**
     * Обновляет данные из календаря. Если время напоминания поменялось — напоминание отправится заново.
     */
    public void update(String title, String location, String description, Instant endAt,
                       boolean allDay, Instant remindAt) {
        this.title = title;
        this.location = location;
        this.description = description;
        this.endAt = endAt;
        this.allDay = allDay;
        if (remindAt == null ? this.remindAt != null : !remindAt.equals(this.remindAt)) {
            this.remindAt = remindAt;
            this.reminderSent = false;
        }
    }

    public Long getId() {
        return id;
    }

    public CalendarSubscription getSubscription() {
        return subscription;
    }

    public String getUid() {
        return uid;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(boolean reminderSent) {
        this.reminderSent = reminderSent;
    }
}
