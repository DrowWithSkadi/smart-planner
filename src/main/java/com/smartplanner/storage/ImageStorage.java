package com.smartplanner.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Хранит картинки событий в папке на диске (по умолчанию ./data/images).
 *
 * <p>Имя файла — случайный UUID, поэтому адрес картинки нельзя угадать.
 * Тип файла определяем по первым байтам («магическим числам»), а не по тому,
 * что прислал браузер: так под видом картинки не загрузить что-то другое.</p>
 */
@Component
public class ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

    /** Имена только такого вида: uuid.ext — защита от путей вроде ../../secret */
    private static final Pattern SAFE_NAME = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp|gif)$");

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path dir;

    public ImageStorage(@Value("${app.storage.images-dir:./data/images}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    public Path dir() {
        return dir;
    }

    /**
     * Сохраняет картинку и возвращает имя файла.
     *
     * @throws IllegalArgumentException если это не картинка или она слишком большая
     */
    public String save(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Файл пустой");
        }
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Картинка больше 5 МБ");
        }
        String ext = detectType(data)
                .orElseThrow(() -> new IllegalArgumentException("Поддерживаются картинки JPEG, PNG, WebP и GIF"));
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + "." + ext;
        Files.write(dir.resolve(name), data);
        return name;
    }

    /** Путь к файлу или пусто, если имени нет или файл пропал. */
    public Optional<Path> resolve(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path path = dir.resolve(name);
        return Files.exists(path) ? Optional.of(path) : Optional.empty();
    }

    /** Удаляет файл. Ошибки не страшны — только пишем в лог. */
    public void delete(String name) {
        resolve(name).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Не удалось удалить картинку {}", name, e);
            }
        });
    }

    /** Определяет тип картинки по первым байтам файла. */
    public static Optional<String> detectType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return Optional.of("jpg");
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return Optional.of("png");
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return Optional.of("webp");
        }
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8'
                && (d[4] == '7' || d[4] == '9') && d[5] == 'a') {
            return Optional.of("gif");
        }
        return Optional.empty();
    }
}
