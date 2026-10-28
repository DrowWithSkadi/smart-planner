package com.smartplanner.task;

import com.smartplanner.user.AppUser;
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

import java.time.Duration;
import java.time.Instant;

/**
 * Задача пользователя. Время хранится в UTC (Instant),
 * а в часовой пояс пользователя переводится только при показе.
 */
@Entity
@Table(name = "task", indexes = @Index(name = "idx_task_remind_at", columnList = "remindAt"))
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    /** Когда задача должна быть выполнена. Может быть пустым — тогда это просто заметка. */
    private Instant dueAt;

    /** За сколько минут до dueAt напомнить. */
    private Integer remindBeforeMinutes;

    /** Точный момент отправки напоминания (вычисляется из dueAt и remindBeforeMinutes). */
    private Instant remindAt;

    private boolean reminderSent;

    private boolean done;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Task() {
        // нужен для JPA
    }

    public Task(AppUser user, String title) {
        this.user = user;
        this.title = title;
    }

    /** Пересчитывает время напоминания. Вызывать после изменения dueAt или remindBeforeMinutes. */
    public void recalculateReminder() {
        if (dueAt == null) {
            remindAt = null;
        } else {
            int minutes = remindBeforeMinutes == null ? 0 : remindBeforeMinutes;
            remindAt = dueAt.minus(Duration.ofMinutes(minutes));
        }
        reminderSent = false;
    }

    /** Откладывает напоминание: оно придёт ещё раз через указанное число минут. */
    public void snooze(Duration duration) {
        remindAt = Instant.now().plus(duration);
        reminderSent = false;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Integer getRemindBeforeMinutes() {
        return remindBeforeMinutes;
    }

    public void setRemindBeforeMinutes(Integer remindBeforeMinutes) {
        this.remindBeforeMinutes = remindBeforeMinutes;
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

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
