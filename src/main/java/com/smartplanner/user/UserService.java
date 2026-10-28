package com.smartplanner.user;

import com.smartplanner.common.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Заодно обновляет имя и чат — они могут поменяться.
     */
    @Transactional
    public AppUser findOrCreate(Long telegramId, Long chatId, String firstName, String username) {
        AppUser user = repository.findByTelegramId(telegramId)
                .orElseGet(() -> new AppUser(telegramId, chatId, firstName, username, defaultTimezone));
        user.setChatId(chatId);
        user.setFirstName(firstName);
        user.setUsername(username);
        return repository.save(user);
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
