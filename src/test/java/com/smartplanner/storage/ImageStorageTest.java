package com.smartplanner.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Test
    void detectsImageTypesByContent() {
        assertEquals(Optional.of("png"), ImageStorage.detectType(PNG));
        assertEquals(Optional.of("jpg"), ImageStorage.detectType(JPEG));
        assertEquals(Optional.of("webp"), ImageStorage.detectType("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(Optional.of("gif"), ImageStorage.detectType("GIF89a....".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void rejectsNonImages() {
        assertTrue(ImageStorage.detectType("<html><script>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(ImageStorage.detectType(new byte[0]).isEmpty());
    }

    @Test
    void savesResolvesAndDeletes() throws Exception {
        Path dir = Files.createTempDirectory("images");
        ImageStorage storage = new ImageStorage(dir.toString());

        String name = storage.save(PNG);
        assertTrue(name.endsWith(".png"));
        assertTrue(storage.resolve(name).isPresent());

        storage.delete(name);
        assertFalse(storage.resolve(name).isPresent());
    }

    @Test
    void unsafeNamesAreIgnored() throws Exception {
        ImageStorage storage = new ImageStorage(Files.createTempDirectory("images").toString());
        assertTrue(storage.resolve("../../etc/passwd").isEmpty());
        assertTrue(storage.resolve(null).isEmpty());
    }

    @Test
    void rejectsTextFiles() throws Exception {
        ImageStorage storage = new ImageStorage(Files.createTempDirectory("images").toString());
        assertThrows(IllegalArgumentException.class, () -> storage.save("hello".getBytes(StandardCharsets.UTF_8)));
    }
}
