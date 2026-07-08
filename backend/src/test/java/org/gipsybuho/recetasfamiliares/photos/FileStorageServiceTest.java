package org.gipsybuho.recetasfamiliares.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

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
                imageBytes("jpg")), "avatars");
        FileStorageService.StoredFile png = service.store(file("avatar.png", "image/png",
                imageBytes("png")), "avatars");

        assertThat(jpeg.url()).endsWith(".jpg");
        assertThat(png.url()).endsWith(".png");
        assertThat(Files.list(tempDir.resolve("avatars")).count()).isEqualTo(2);
    }

    @Test
    void rejectsAllowedContentTypeWhenMagicBytesDoNotMatch() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString(), "http://localhost:8080");

        assertThatThrownBy(() -> service.store(file("fake.jpg", "image/jpeg",
                "not actually an image".getBytes()), "avatars"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid image file");
    }

    @Test
    void deletesStoredPathInsideUploadDirOnly() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString(), "http://localhost:8080");

        FileStorageService.StoredFile stored = service.store(file("avatar.jpg", "image/jpeg",
                imageBytes("jpg")), "avatars");
        Path storedPath = tempDir.resolve(stored.storagePath().substring("/uploads/".length()));

        assertThat(Files.exists(storedPath)).isTrue();

        service.deleteStoredPath(stored.storagePath());
        service.deleteStoredPath("/uploads/../outside.jpg");

        assertThat(Files.exists(storedPath)).isFalse();
    }

    private MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }
}
