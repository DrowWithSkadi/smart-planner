package com.smartplanner.webapp;

import com.smartplanner.bot.BotProperties;
import com.smartplanner.user.AppUser;
import com.smartplanner.user.UserService;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Duration;
import java.time.Instant;

/**
 * Определяет пользователя для параметров с @CurrentUser.
 *
 * <p>Mini App присылает заголовок {@code Authorization: tma <initData>}. Мы проверяем подпись
 * Telegram и находим (или создаём) пользователя. Пароли и сессии не нужны.</p>
 */
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private static final String PREFIX = "tma ";

    private final BotProperties botProperties;
    private final WebAppProperties properties;
    private final UserService userService;

    public CurrentUserResolver(BotProperties botProperties, WebAppProperties properties, UserService userService) {
        this.botProperties = botProperties;
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AppUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(PREFIX)) {
            String token = botProperties.token();
            if (token == null || token.isBlank()) {
                throw new UnauthorizedException("На сервере не задан токен бота — проверить вход через Telegram нельзя");
            }
            try {
                TelegramInitData.TelegramUser tg = TelegramInitData.validate(header.substring(PREFIX.length()),
                        token, Duration.ofHours(properties.initDataMaxAgeHours()), Instant.now());
                // в личном чате с ботом ID чата совпадает с ID пользователя
                return userService.findOrCreate(tg.id(), tg.id(), tg.firstName(), tg.username());
            } catch (IllegalArgumentException e) {
                throw new UnauthorizedException(e.getMessage());
            }
        }

        if (properties.devTelegramId() != null) {
            Long id = properties.devTelegramId();
            return userService.findByTelegramId(id)
                    .orElseGet(() -> userService.findOrCreate(id, id, "Разработчик", null));
        }
        throw new UnauthorizedException("Открой планер через Telegram");
    }
}
