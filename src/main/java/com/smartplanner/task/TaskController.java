package com.smartplanner.task;

import com.smartplanner.task.dto.TaskRequest;
import com.smartplanner.task.dto.TaskResponse;
import com.smartplanner.user.AppUser;
import com.smartplanner.webapp.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** REST API событий (задач) текущего пользователя (он определяется по данным Telegram Mini App). */
@RestController
@RequestMapping("/api/me/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** Задачи за период: GET /api/me/tasks?from=2026-10-26&to=2026-11-01 (обе даты включительно). */
    @GetMapping
    public List<TaskResponse> list(@CurrentUser AppUser user,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from) || to.isAfter(from.plusDays(62))) {
            throw new IllegalArgumentException("Период должен быть от 1 до 62 дней");
        }
        return toResponses(user, taskService.findBetween(user.getId(), from, to));
    }

    /** Задачи без даты, которые ещё не сделаны. */
    @GetMapping("/inbox")
    public List<TaskResponse> inbox(@CurrentUser AppUser user) {
        return toResponses(user, taskService.findUndated(user.getId()));
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(@CurrentUser AppUser user, @PathVariable Long taskId) {
        return TaskResponse.from(taskService.get(user.getId(), taskId), user.zoneId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@CurrentUser AppUser user, @Valid @RequestBody TaskRequest request) {
        return TaskResponse.from(taskService.create(user.getId(), request), user.zoneId());
    }

    @PutMapping("/{taskId}")
    public TaskResponse update(@CurrentUser AppUser user, @PathVariable Long taskId,
                               @Valid @RequestBody TaskRequest request) {
        return TaskResponse.from(taskService.update(user.getId(), taskId, request), user.zoneId());
    }

    /** Отметить выполненной или снять отметку: PATCH /api/me/tasks/5/done?value=true */
    @PatchMapping("/{taskId}/done")
    public TaskResponse setDone(@CurrentUser AppUser user, @PathVariable Long taskId,
                                @RequestParam(defaultValue = "true") boolean value) {
        return TaskResponse.from(taskService.setDone(user.getId(), taskId, value), user.zoneId());
    }

    /** Загрузить картинку: POST /api/me/tasks/5/image (multipart, поле file). */
    @PostMapping(value = "/{taskId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TaskResponse uploadImage(@CurrentUser AppUser user, @PathVariable Long taskId,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        return TaskResponse.from(taskService.setImage(user.getId(), taskId, file.getBytes()), user.zoneId());
    }

    @DeleteMapping("/{taskId}/image")
    public TaskResponse deleteImage(@CurrentUser AppUser user, @PathVariable Long taskId) {
        return TaskResponse.from(taskService.removeImage(user.getId(), taskId), user.zoneId());
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AppUser user, @PathVariable Long taskId) {
        taskService.delete(user.getId(), taskId);
    }

    private static List<TaskResponse> toResponses(AppUser user, List<Task> tasks) {
        ZoneId zone = user.zoneId();
        return tasks.stream().map(t -> TaskResponse.from(t, zone)).toList();
    }
}
