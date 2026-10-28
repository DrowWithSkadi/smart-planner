package com.smartplanner.task;

import com.smartplanner.task.dto.TaskRequest;
import com.smartplanner.task.dto.TaskResponse;
import com.smartplanner.user.UserService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * REST API задач. Пока пользователь указывается в адресе (/api/users/{userId}/tasks).
 * Когда сделаем Mini App, пользователь будет определяться по данным Telegram.
 */
@RestController
@RequestMapping("/api/users/{userId}/tasks")
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;

    public TaskController(TaskService taskService, UserService userService) {
        this.taskService = taskService;
        this.userService = userService;
    }

    /** Все задачи или задачи на день: GET /api/users/1/tasks?date=2026-10-29 */
    @GetMapping
    public List<TaskResponse> list(@PathVariable Long userId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ZoneId zone = userService.get(userId).zoneId();
        List<Task> tasks = date == null ? taskService.findAll(userId) : taskService.findForDay(userId, date);
        return tasks.stream().map(t -> TaskResponse.from(t, zone)).toList();
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(@PathVariable Long userId, @PathVariable Long taskId) {
        return toResponse(userId, taskService.get(userId, taskId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@PathVariable Long userId, @Valid @RequestBody TaskRequest request) {
        return toResponse(userId, taskService.create(userId, request));
    }

    @PutMapping("/{taskId}")
    public TaskResponse update(@PathVariable Long userId, @PathVariable Long taskId,
                               @Valid @RequestBody TaskRequest request) {
        return toResponse(userId, taskService.update(userId, taskId, request));
    }

    /** Отметить выполненной или снять отметку: PATCH /api/users/1/tasks/5/done?value=true */
    @PatchMapping("/{taskId}/done")
    public TaskResponse setDone(@PathVariable Long userId, @PathVariable Long taskId,
                                @RequestParam(defaultValue = "true") boolean value) {
        return toResponse(userId, taskService.setDone(userId, taskId, value));
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long userId, @PathVariable Long taskId) {
        taskService.delete(userId, taskId);
    }

    private TaskResponse toResponse(Long userId, Task task) {
        return TaskResponse.from(task, userService.get(userId).zoneId());
    }
}
