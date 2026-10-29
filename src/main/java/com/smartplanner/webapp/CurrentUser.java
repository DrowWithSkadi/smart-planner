package com.smartplanner.webapp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Параметр контроллера с этой аннотацией получает текущего пользователя (AppUser),
 * определённого по данным Telegram Mini App. Пример:
 * <pre>
 *   &#64;GetMapping public List&lt;TaskResponse&gt; list(@CurrentUser AppUser user) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
