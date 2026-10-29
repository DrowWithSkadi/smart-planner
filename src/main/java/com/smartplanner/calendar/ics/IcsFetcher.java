package com.smartplanner.calendar.ics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Скачивает .ics файл по ссылке.
 * Умеет в «условные запросы»: если календарь не менялся, сервер отвечает 304 и ничего не присылает.
 */
public class IcsFetcher {

    private final HttpClient client;
    private final long maxBytes;

    public IcsFetcher(long maxBytes) {
        this.maxBytes = maxBytes;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * @param notModified true — календарь не изменился с прошлого раза, body пустой
     * @param etag        метка версии от сервера, передаём её при следующем запросе
     */
    public record Result(boolean notModified, String body, String etag, String lastModified) {
    }

    /**
     * Превращает ссылку от пользователя в нормальный https-адрес.
     * webcal:// — это тот же https, просто так помечают ссылки «для подписки».
     */
    public static URI normalize(String url) throws IcsException {
        if (url == null || url.isBlank()) {
            throw new IcsException("Ссылка пустая");
        }
        String value = url.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("webcal://")) {
            value = "https://" + value.substring("webcal://".length());
        } else if (lower.startsWith("webcals://")) {
            value = "https://" + value.substring("webcals://".length());
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) {
                throw new IcsException("Ссылка должна начинаться с https://, http:// или webcal://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IcsException("В ссылке нет адреса сайта");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IcsException("Некорректная ссылка", e);
        }
    }

    public Result fetch(URI uri, String etag, String lastModified) throws IcsException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "SmartPlanner/0.1 (calendar sync)")
                .header("Accept", "text/calendar, */*;q=0.5")
                .GET();
        if (etag != null) {
            request.header("If-None-Match", etag);
        }
        if (lastModified != null) {
            request.header("If-Modified-Since", lastModified);
        }

        try {
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            try (InputStream in = response.body()) {
                if (status == 304) {
                    return new Result(true, null, etag, lastModified);
                }
                if (status == 401 || status == 403) {
                    throw new IcsException("Нет доступа к календарю (код " + status + "). Ссылка должна быть публичной");
                }
                if (status == 404) {
                    throw new IcsException("Календарь не найден по этой ссылке (код 404)");
                }
                if (status < 200 || status >= 300) {
                    throw new IcsException("Сервер календаря ответил ошибкой (код " + status + ")");
                }
                String body = readLimited(in);
                return new Result(false, body,
                        response.headers().firstValue("ETag").orElse(null),
                        response.headers().firstValue("Last-Modified").orElse(null));
            }
        } catch (IOException e) {
            throw new IcsException("Не удалось скачать календарь: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IcsException("Загрузка календаря прервана", e);
        }
    }

    private String readLimited(InputStream in) throws IOException, IcsException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IcsException("Календарь слишком большой (больше " + maxBytes / 1024 / 1024 + " МБ)");
            }
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
