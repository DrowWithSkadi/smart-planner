package com.smartplanner.webapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Проверка данных, которые Telegram передаёт в Mini App (Telegram.WebApp.initData).
 *
 * <p>Telegram подписывает эти данные токеном бота. Если подпись сошлась — мы точно знаем,
 * какой пользователь открыл приложение, и регистрация с паролем не нужна.
 * Алгоритм описан в документации: core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app</p>
 */
public final class TelegramInitData {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TelegramInitData() {
    }

    /** Пользователь Telegram из initData. */
    public record TelegramUser(long id, String firstName, String username, String languageCode) {
    }

    /**
     * @param initData строка Telegram.WebApp.initData (вида query_id=…&user=…&auth_date=…&hash=…)
     * @param botToken токен бота
     * @param maxAge   сколько времени данные считаются свежими
     * @param now      текущее время (параметром — чтобы было удобно тестировать)
     * @throws IllegalArgumentException если подпись неверная, данные устарели или повреждены
     */
    public static TelegramUser validate(String initData, String botToken, Duration maxAge, Instant now) {
        if (initData == null || initData.isBlank()) {
            throw new IllegalArgumentException("Нет данных авторизации Telegram");
        }
        Map<String, String> fields = parse(initData);
        String hash = fields.remove("hash");
        if (hash == null) {
            throw new IllegalArgumentException("В данных Telegram нет подписи");
        }

        // Строка для проверки: все поля, кроме hash, по алфавиту, в виде key=value через перевод строки
        String dataCheckString = fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));

        byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
                botToken.getBytes(StandardCharsets.UTF_8));
        byte[] expected = hmacSha256(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(hash);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Подпись Telegram повреждена");
        }
        // MessageDigest.isEqual сравнивает за постоянное время — так подпись нельзя подобрать по таймингу
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("Подпись Telegram не совпала");
        }

        long authDate = parseLong(fields.get("auth_date"));
        if (Instant.ofEpochSecond(authDate).plus(maxAge).isBefore(now)) {
            throw new IllegalArgumentException("Данные Telegram устарели — открой приложение заново");
        }

        String userJson = fields.get("user");
        if (userJson == null) {
            throw new IllegalArgumentException("В данных Telegram нет пользователя");
        }
        try {
            JsonNode user = JSON.readTree(userJson);
            return new TelegramUser(user.path("id").asLong(),
                    text(user, "first_name"), text(user, "username"), text(user, "language_code"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось прочитать пользователя Telegram");
        }
    }

    /** Разбирает query-строку в отсортированную карту (TreeMap сортирует ключи по алфавиту). */
    static Map<String, String> parse(String initData) {
        Map<String, String> result = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 недоступен", e);
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("В данных Telegram нет корректной даты авторизации");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
