package com.smartplanner.task;

import com.smartplanner.common.NotFoundException;
import com.smartplanner.task.dto.TaskRequest;
import com.smartplanner.user.AppUser;
import com.smartplanner.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Вся логика работы с задачами. Её используют и REST API, и Telegram-бот. */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserService userService;
    private final int defaultRemindBeforeMinutes;

    public TaskService(TaskRepository taskRepository,
                       UserService userService,
                       @Value("${app.reminders.default-before-minutes:15}") int defaultRemindBeforeMinutes) {
        this.taskRepository = taskRepository;
        this.userService = userService;
        this.defaultRemindBeforeMinutes = defaultRemindBeforeMinutes;
    }

    @Transactional
    public Task create(Long userId, TaskRequest request) {
        AppUser user = userService.get(userId);
        Task task = new Task(user, request.title().trim());
        apply(task, request, user.zoneId());
        return taskRepository.save(task);
    }

    @Transactional
    public Task update(Long userId, Long taskId, TaskRequest request) {
        AppUser user = userService.get(userId);
        Task task = get(userId, taskId);
        task.setTitle(request.title().trim());
        apply(task, request, user.zoneId());
        return task;
    }

    @Transactional(readOnly = true)
    public Task get(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new NotFoundException("Задача " + taskId + " не найдена"));
    }

    @Transactional(readOnly = true)
    public List<Task> findAll(Long userId) {
        return taskRepository.findByUserIdOrderByDueAtAsc(userId);
    }

    /** Задачи на конкретный день (по часовому поясу пользователя). */
    @Transactional(readOnly = true)
    public List<Task> findForDay(Long userId, LocalDate day) {
        ZoneId zone = userService.get(userId).zoneId();
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        return taskRepository.findByUserIdAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(userId, from, to);
    }

    /** Задачи за период дат включительно (по часовому поясу пользователя). */
    @Transactional(readOnly = true)
    public List<Task> findBetween(Long userId, LocalDate from, LocalDate to) {
        ZoneId zone = userService.get(userId).zoneId();
        return taskRepository.findByUserIdAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(userId,
                from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** Невыполненные задачи без даты — «входящие». */
    @Transactional(readOnly = true)
    public List<Task> findUndated(Long userId) {
        return taskRepository.findByUserIdAndDueAtIsNullAndDoneFalseOrderByCreatedAtAsc(userId);
    }

    /** Ближайшие невыполненные задачи. */
    @Transactional(readOnly = true)
    public List<Task> findUpcoming(Long userId) {
        return taskRepository.findTop10ByUserIdAndDoneFalseAndDueAtGreaterThanEqualOrderByDueAtAsc(userId, Instant.now());
    }

    @Transactional
    public Task setDone(Long userId, Long taskId, boolean done) {
        Task task = get(userId, taskId);
        task.setDone(done);
        return task;
    }

    @Transactional
    public Task snooze(Long userId, Long taskId, int minutes) {
        Task task = get(userId, taskId);
        task.snooze(Duration.ofMinutes(minutes));
        return task;
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        taskRepository.delete(get(userId, taskId));
    }

    private void apply(Task task, TaskRequest request, ZoneId zone) {
        task.setDescription(request.description());
        LocalDateTime due = request.dueAt();
        task.setDueAt(due == null ? null : due.atZone(zone).toInstant());
        task.setRemindBeforeMinutes(request.remindBeforeMinutes() != null
                ? request.remindBeforeMinutes()
                : defaultRemindBeforeMinutes);
        task.recalculateReminder();
    }
}
