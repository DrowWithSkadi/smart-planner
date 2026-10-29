package com.smartplanner.user;

import com.smartplanner.common.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final AppUserRepository repository;
    private final String defaultTimezone;

    public UserService(AppUserRepository repository,
                       @Value("${app.default-timezone:Europe/Tallinn}") String defaultTimezone) {
        this.repository = repository;
        this.defaultTimezone = defaultTimezone;
    }

    /**
     * Находит пользователя по Telegram ID или создаёт нового.
     * Заодно обновляет имя и чат — они могут поменяться. Пишет в базу, только если что-то изменилось.
     */
    @Transactional
    public AppUser findOrCreate(Long telegramId, Long chatId, String firstName, String username) {
        Optional<AppUser> existing = repository.findByTelegramId(telegramId);
        if (existing.isEmpty()) {
            return repository.save(new AppUser(telegramId, chatId, firstName, username, defaultTimezone));
        }
        AppUser user = existing.get();
        user.setChatId(chatId);
        user.setFirstName(firstName);
        user.setUsername(username);
        return user; // изменения сохранятся сами при завершении транзакции (если они были)
    }

    /** Меняет часовой пояс, например на "Europe/Tallinn". */
    @Transactional
    public AppUser setTimezone(Long userId, String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Неизвестный часовой пояс: " + timezone);
        }
        AppUser user = get(userId);
        user.setTimezone(timezone);
        return user;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByTelegramId(Long telegramId) {
        return repository.findByTelegramId(telegramId);
    }

    @Transactional(readOnly = true)
    public AppUser get(Long userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь " + userId + " не найден"));
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return repository.findAll();
    }
}
