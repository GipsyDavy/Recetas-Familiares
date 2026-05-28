package org.gipsybuho.recetasfamiliares.photos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileStorageService {

    private static final long MAX_BYTES = 8 * 1024 * 1024L; // 8 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp");

    private final Path uploadDir;
    private final String baseUrl;

    public FileStorageService(
            @Value("${app.upload.dir:./uploads}") String uploadDirPath,
            @Value("${app.upload.base-url:http://localhost:8080}") String baseUrl
    ) throws IOException {
        this.uploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        Files.createDirectories(this.uploadDir);
    }

    public record StoredFile(String url, String contentType, long sizeBytes) {}

    public StoredFile store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File exceeds 8 MB limit");
        }
        String contentType = file.getContentType() == null
                ? "image/jpeg"
                : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Allowed: JPEG, PNG, WebP");
        }
        String ext = switch (contentType) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default           -> ".jpg";
        };
        String filename = UUID.randomUUID() + ext;
        Path target = uploadDir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredFile(baseUrl + "/uploads/" + filename, contentType, file.getSize());
    }
}
