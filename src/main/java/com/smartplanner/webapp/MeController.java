package com.smartplanner.webapp;

import com.smartplanner.user.AppUser;
import com.smartplanner.user.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Профиль текущего пользователя Mini App. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    public record MeResponse(Long id, String firstName, String username, String timezone) {
        static MeResponse from(AppUser u) {
            return new MeResponse(u.getId(), u.getFirstName(), u.getUsername(), u.getTimezone());
        }
    }

    public record UpdateRequest(String timezone) {
    }

    @GetMapping
    public MeResponse me(@CurrentUser AppUser user) {
        return MeResponse.from(user);
    }

    /** Mini App сам присылает часовой пояс устройства, например "Europe/Tallinn". */
    @PatchMapping
    public MeResponse update(@CurrentUser AppUser user, @RequestBody UpdateRequest request) {
        if (request.timezone() != null) {
            return MeResponse.from(userService.setTimezone(user.getId(), request.timezone()));
        }
        return MeResponse.from(user);
    }
}
