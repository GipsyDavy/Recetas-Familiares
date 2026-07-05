package org.gipsybuho.recetasfamiliares.photos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public record StoredFile(String url, String storagePath, String contentType, long sizeBytes) {}

    public StoredFile store(MultipartFile file) throws IOException {
        return store(file, null);
    }

    public StoredFile store(MultipartFile file, String subdir) throws IOException {
        if (subdir != null && !subdir.matches("[a-zA-Z0-9_-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subdir");
        }
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
        byte[] bytes = file.getBytes();
        validateMagicBytes(bytes, contentType);
        String ext = switch (contentType) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default           -> ".jpg";
        };
        String filename = UUID.randomUUID() + ext;
        Path targetDir = (subdir != null && !subdir.isBlank())
                ? uploadDir.resolve(subdir)
                : uploadDir;
        Files.createDirectories(targetDir);
        Files.write(targetDir.resolve(filename), bytes);
        String urlPath = (subdir != null && !subdir.isBlank())
                ? "/uploads/" + subdir + "/" + filename
                : "/uploads/" + filename;
        return new StoredFile(baseUrl + urlPath, urlPath, contentType, bytes.length);
    }

    private void validateMagicBytes(byte[] bytes, String contentType) {
        if (bytes.length < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image file");
        }
        boolean valid = switch (contentType) {
            // JPEG: FF D8 FF
            case "image/jpeg", "image/jpg" ->
                    (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            case "image/png" ->
                    (bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50
                            && (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47
                            && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                            && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A;
            // WebP: RIFF (bytes 0-3) + WEBP (bytes 8-11)
            case "image/webp" ->
                    (bytes[0] & 0xFF) == 0x52 && (bytes[1] & 0xFF) == 0x49
                            && (bytes[2] & 0xFF) == 0x46 && (bytes[3] & 0xFF) == 0x46
                            && (bytes[8] & 0xFF) == 0x57 && (bytes[9] & 0xFF) == 0x45
                            && (bytes[10] & 0xFF) == 0x42 && (bytes[11] & 0xFF) == 0x50;
            default -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File content does not match declared type");
        }
    }
}
