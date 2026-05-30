package org.gipsybuho.recetasfamiliares.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAllowedImagesWhenMagicBytesMatchContentType() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString(), "http://localhost:8080");

        FileStorageService.StoredFile jpeg = service.store(file("avatar.jpg", "image/jpeg",
                bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0)), "avatars");
        FileStorageService.StoredFile png = service.store(file("avatar.png", "image/png",
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)), "avatars");
        FileStorageService.StoredFile webp = service.store(file("avatar.webp", "image/webp",
                bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)), "avatars");

        assertThat(jpeg.url()).endsWith(".jpg");
        assertThat(png.url()).endsWith(".png");
        assertThat(webp.url()).endsWith(".webp");
        assertThat(Files.list(tempDir.resolve("avatars")).count()).isEqualTo(3);
    }

    @Test
    void rejectsAllowedContentTypeWhenMagicBytesDoNotMatch() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString(), "http://localhost:8080");

        assertThatThrownBy(() -> service.store(file("fake.jpg", "image/jpeg",
                "not actually an image".getBytes()), "avatars"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("File content does not match declared type");
    }

    private MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
