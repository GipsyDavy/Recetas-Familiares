package org.gipsybuho.recetasfamiliares.photos;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileStorageService {

    private static final long MAX_BYTES = 8 * 1024 * 1024L; // 8 MB
    private static final int MAX_WIDTH = 8_000;
    private static final int MAX_HEIGHT = 8_000;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final int THUMBNAIL_MAX_DIMENSION = 512;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

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

    public record StoredFile(
            String url,
            String storagePath,
            String contentType,
            long sizeBytes,
            String thumbnailUrl,
            String thumbnailStoragePath,
            Integer width,
            Integer height
    ) {
    }

    public StoredFile store(MultipartFile file) throws IOException {
        return store(file, null);
    }

    public StoredFile store(MultipartFile file, String subdir) throws IOException {
        return storeValidatedImage(file, subdir, null);
    }

    public StoredFile storeWithThumbnail(MultipartFile file, String subdir, String thumbnailSubdir)
            throws IOException {
        if (thumbnailSubdir == null || thumbnailSubdir.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid thumbnail subdir");
        }
        return storeValidatedImage(file, subdir, thumbnailSubdir);
    }

    private StoredFile storeValidatedImage(MultipartFile file, String subdir, String thumbnailSubdir)
            throws IOException {
        requireSafeSubdir(subdir);
        requireSafeSubdir(thumbnailSubdir);
        ValidatedImage validated = validateAndDecode(file);
        EncodedImage encoded = encodeOriginal(validated);
        if (encoded.bytes().length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds 8 MB limit");
        }

        StoredBytes original = writeStoredBytes(encoded.bytes(), encoded.extension(), subdir);
        try {
            StoredBytes thumbnail = null;
            if (thumbnailSubdir != null && !thumbnailSubdir.isBlank()) {
                byte[] thumbnailBytes = createThumbnail(validated.image());
                thumbnail = writeStoredBytes(thumbnailBytes, ".jpg", thumbnailSubdir);
            }

            return new StoredFile(
                    original.url(),
                    original.storagePath(),
                    encoded.contentType(),
                    encoded.bytes().length,
                    thumbnail != null ? thumbnail.url() : null,
                    thumbnail != null ? thumbnail.storagePath() : null,
                    validated.width(),
                    validated.height()
            );
        } catch (IOException | RuntimeException e) {
            deleteStoredPath(original.storagePath());
            throw e;
        }
    }

    public void deleteStoredPath(String storagePath) {
        if (storagePath == null || !storagePath.startsWith("/uploads/")) {
            return;
        }
        Path relative = Path.of(storagePath.substring("/uploads/".length())).normalize();
        if (relative.isAbsolute()) {
            return;
        }
        Path target = uploadDir.resolve(relative).normalize();
        if (!target.startsWith(uploadDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Best-effort cleanup. Access control still fails closed for orphaned paths.
        }
    }

    private ValidatedImage validateAndDecode(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds 8 MB limit");
        }
        String extensionType = contentTypeFromExtension(file.getOriginalFilename());
        String declaredType = normalizeContentType(file.getContentType());
        byte[] bytes = file.getBytes();
        if (bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds 8 MB limit");
        }

        ImageType detectedType = detectType(bytes);
        if (!detectedType.contentType().equals(extensionType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File extension does not match image content");
        }
        if (declaredType != null && !detectedType.contentType().equals(declaredType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File content does not match declared type");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                requireSafeDimensions(width, height);
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
                return new ValidatedImage(detectedType, width, height, image);
            } finally {
                reader.dispose();
            }
        }
    }

    private EncodedImage encodeOriginal(ValidatedImage validated) throws IOException {
        // WebP se acepta como entrada, pero se normaliza a JPEG para almacenar
        // una imagen pasiva, sin metadata y soportada por todos los clientes.
        String format = validated.type() == ImageType.PNG ? "png" : "jpg";
        String contentType = validated.type() == ImageType.PNG ? "image/png" : "image/jpeg";
        BufferedImage output = "jpg".equals(format) ? toRgb(validated.image()) : validated.image();
        byte[] bytes = writeImage(output, format);
        return new EncodedImage(bytes, "jpg".equals(format) ? ".jpg" : ".png", contentType);
    }

    private byte[] createThumbnail(BufferedImage source) throws IOException {
        double scale = Math.min(
                1.0,
                THUMBNAIL_MAX_DIMENSION / (double) Math.max(source.getWidth(), source.getHeight())
        );
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage thumb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return writeImage(thumb, "jpg");
    }

    private StoredBytes writeStoredBytes(byte[] bytes, String extension, String subdir) throws IOException {
        Path targetDir = (subdir != null && !subdir.isBlank())
                ? uploadDir.resolve(subdir).normalize()
                : uploadDir;
        if (!targetDir.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upload path");
        }
        Files.createDirectories(targetDir);
        String filename = UUID.randomUUID() + extension;
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(targetDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upload path");
        }
        Files.write(target, bytes);
        String urlPath = (subdir != null && !subdir.isBlank())
                ? "/uploads/" + subdir + "/" + filename
                : "/uploads/" + filename;
        return new StoredBytes(baseUrl + urlPath, urlPath);
    }

    private static byte[] writeImage(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image encoder");
        }
        return out.toByteArray();
    }

    private static BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(source, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static ImageType detectType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A) {
            return ImageType.PNG;
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return ImageType.WEBP;
        }
        throw invalidImage();
    }

    private static void requireSafeDimensions(int width, int height) {
        long pixels = (long) width * (long) height;
        if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT || pixels > MAX_PIXELS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image dimensions exceed the allowed limit");
        }
    }

    private static String contentTypeFromExtension(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String type;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            type = "image/jpeg";
        } else if (lower.endsWith(".png")) {
            type = "image/png";
        } else if (lower.endsWith(".webp")) {
            type = "image/webp";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file extension. Allowed: JPEG, PNG, WebP");
        }
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file extension. Allowed: JPEG, PNG, WebP");
        }
        return type;
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (!ALLOWED_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Allowed: JPEG, PNG, WebP");
        }
        return normalized;
    }

    private static void requireSafeSubdir(String subdir) {
        if (subdir != null && !subdir.matches("[a-zA-Z0-9_-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subdir");
        }
    }

    private static ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image file");
    }

    private enum ImageType {
        JPEG("image/jpeg"),
        PNG("image/png"),
        WEBP("image/webp");

        private final String contentType;

        ImageType(String contentType) {
            this.contentType = contentType;
        }

        String contentType() {
            return contentType;
        }
    }

    private record ValidatedImage(ImageType type, int width, int height, BufferedImage image) {
    }

    private record EncodedImage(byte[] bytes, String extension, String contentType) {
    }

    private record StoredBytes(String url, String storagePath) {
    }
}
