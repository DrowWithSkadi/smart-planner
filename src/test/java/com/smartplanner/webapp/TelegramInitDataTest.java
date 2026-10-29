package com.smartplanner.webapp;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramInitDataTest {

    private static final String TOKEN = "123456:TEST-token";
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final Duration DAY = Duration.ofHours(24);
    private static final String USER = "{\"id\":42,\"first_name\":\"Тихон\",\"username\":\"tikhon\",\"language_code\":\"ru\"}";

    /** Собирает initData так же, как это делает Telegram, и подписывает его токеном. */
    private static String signed(String token, Map<String, String> fields) {
        Map<String, String> sorted = new TreeMap<>(fields);
        String check = sorted.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        byte[] secret = TelegramInitData.hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
        String hash = HexFormat.of().formatHex(TelegramInitData.hmacSha256(secret, check.getBytes(StandardCharsets.UTF_8)));
        sorted.put("hash", hash);
        return sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static Map<String, String> fields(long authDate) {
        return Map.of("query_id", "AAE123", "user", USER, "auth_date", String.valueOf(authDate));
    }

    @Test
    void validDataReturnsUser() {
        String initData = signed(TOKEN, fields(NOW.minusSeconds(60).getEpochSecond()));
        TelegramInitData.TelegramUser user = TelegramInitData.validate(initData, TOKEN, DAY, NOW);
        assertEquals(42, user.id());
        assertEquals("Тихон", user.firstName());
        assertEquals("tikhon", user.username());
    }

    @Test
    void wrongTokenIsRejected() {
        String initData = signed("999:other-bot", fields(NOW.getEpochSecond()));
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.validate(initData, TOKEN, DAY, NOW));
    }

    @Test
    void tamperedUserIsRejected() {
        String initData = signed(TOKEN, fields(NOW.getEpochSecond()))
                .replace(URLEncoder.encode("\"id\":42", StandardCharsets.UTF_8),
                        URLEncoder.encode("\"id\":43", StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.validate(initData, TOKEN, DAY, NOW));
    }

    @Test
    void expiredDataIsRejected() {
        String initData = signed(TOKEN, fields(NOW.minus(Duration.ofHours(25)).getEpochSecond()));
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.validate(initData, TOKEN, DAY, NOW));
    }

    @Test
    void missingHashIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TelegramInitData.validate("user=%7B%7D&auth_date=1", TOKEN, DAY, NOW));
        assertThrows(IllegalArgumentException.class, () -> TelegramInitData.validate("", TOKEN, DAY, NOW));
    }
}
