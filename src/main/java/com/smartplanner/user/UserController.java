package com.smartplanner.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Список пользователей — пока для отладки, чтобы узнать свой userId.
 * Когда появится Mini App, пользователь будет определяться через данные Telegram.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.findAll().stream().map(UserResponse::from).toList();
    }

    public record UserResponse(Long id, Long telegramId, String firstName, String username, String timezone) {
        static UserResponse from(AppUser u) {
            return new UserResponse(u.getId(), u.getTelegramId(), u.getFirstName(), u.getUsername(), u.getTimezone());
        }
    }
}
