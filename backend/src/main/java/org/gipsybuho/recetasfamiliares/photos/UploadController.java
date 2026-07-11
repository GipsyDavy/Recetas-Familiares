package org.gipsybuho.recetasfamiliares.photos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.gipsybuho.recetasfamiliares.chat.ChatAttachmentRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sirve archivos de /uploads/** con ownership: cada archivo solo es accesible
 * para usuarios con relacion familiar con el recurso (SEC-3). Los archivos
 * huerfanos (sin registro en base de datos) devuelven 404.
 */
@RestController
public class UploadController {

    /** Solo nombres UUID + extension de la allowlist: bloquea path traversal. */
    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|webp)$");

    private final Path uploadDir;
    private final RecipePhotoRepository photoRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;
    private final String uploadBaseUrl;

    public UploadController(
            @Value("${app.upload.dir:./uploads}") String uploadDirPath,
            @Value("${app.upload.base-url:http://localhost:8080}") String uploadBaseUrl,
            RecipePhotoRepository photoRepository,
            ChatAttachmentRepository chatAttachmentRepository,
            UserRepository userRepository,
            FamilyMemberRepository familyMemberRepository,
            FamilyRepository familyRepository
    ) {
        this.uploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
        this.uploadBaseUrl = trimTrailingSlash(uploadBaseUrl);
        this.photoRepository = photoRepository;
        this.chatAttachmentRepository = chatAttachmentRepository;
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
    }

    @GetMapping("/uploads/{filename}")
    public ResponseEntity<byte[]> recipePhoto(@PathVariable String filename, Authentication authentication) {
        requireSafeFilename(filename);
        String userId = authentication.getName();
        String localPath = "/uploads/" + filename;
        List<String> owningFamilyIds = photoRepository.findOwningFamilyIdsByStoragePath(localPath);
        boolean allowed = owningFamilyIds.stream()
                .anyMatch(familyId -> familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId));
        if (!allowed) {
            throw notFound();
        }
        return serveFile(uploadDir.resolve(filename), filename);
    }

    @GetMapping("/uploads/avatars/{filename}")
    public ResponseEntity<byte[]> avatar(@PathVariable String filename, Authentication authentication) {
        requireSafeFilename(filename);
        String requesterId = authentication.getName();
        String localPath = "/uploads/avatars/" + filename;
        List<String> ownerIds = userRepository.findIdsByAvatarLocalUrl(
                localUrl(localPath),
                localPath
        );
        boolean allowed = ownerIds.contains(requesterId) || sharesFamilyWithAny(requesterId, ownerIds);
        if (!allowed) {
            throw notFound();
        }
        return serveFile(uploadDir.resolve("avatars").resolve(filename), filename);
    }

    @GetMapping("/uploads/family_avatars/{filename}")
    public ResponseEntity<byte[]> familyAvatar(@PathVariable String filename, Authentication authentication) {
        requireSafeFilename(filename);
        String requesterId = authentication.getName();
        String localPath = "/uploads/family_avatars/" + filename;
        List<String> owningFamilyIds = familyRepository.findIdsByAvatarLocalUrl(localUrl(localPath), localPath);
        boolean allowed = owningFamilyIds.stream()
                .anyMatch(familyId -> familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, requesterId));
        if (!allowed) {
            throw notFound();
        }
        return serveFile(uploadDir.resolve("family_avatars").resolve(filename), filename);
    }

    @GetMapping("/uploads/chat/{filename}")
    public ResponseEntity<byte[]> chatImage(@PathVariable String filename, Authentication authentication) {
        requireSafeFilename(filename);
        requireChatAttachmentAccess("/uploads/chat/" + filename, authentication.getName());
        return serveFile(uploadDir.resolve("chat").resolve(filename), filename);
    }

    @GetMapping("/uploads/chat_thumbnails/{filename}")
    public ResponseEntity<byte[]> chatThumbnail(@PathVariable String filename, Authentication authentication) {
        requireSafeFilename(filename);
        requireChatAttachmentAccess("/uploads/chat_thumbnails/" + filename, authentication.getName());
        return serveFile(uploadDir.resolve("chat_thumbnails").resolve(filename), filename);
    }

    private boolean sharesFamilyWithAny(String requesterId, List<String> ownerIds) {
        if (ownerIds.isEmpty()) {
            return false;
        }
        Set<String> requesterFamilies = Set.copyOf(familyMemberRepository.findFamilyIdsByUserId(requesterId));
        if (requesterFamilies.isEmpty()) {
            return false;
        }
        return ownerIds.stream()
                .anyMatch(ownerId -> familyMemberRepository.findFamilyIdsByUserId(ownerId).stream()
                        .anyMatch(requesterFamilies::contains));
    }

    private void requireChatAttachmentAccess(String storagePath, String requesterId) {
        List<String> owningFamilyIds = chatAttachmentRepository.findOwningFamilyIdsByStoragePath(storagePath);
        boolean allowed = owningFamilyIds.stream()
                .anyMatch(familyId -> familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(
                        familyId, requesterId));
        if (!allowed) {
            throw notFound();
        }
    }

    private ResponseEntity<byte[]> serveFile(Path file, String filename) {
        Path normalized = file.normalize();
        Path resolved = resolveRegularFile(normalized);
        if (resolved == null) {
            throw notFound();
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
        return ResponseEntity.ok()
                .contentType(contentTypeFor(filename))
                .header("Cache-Control", "private, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    private Path resolveRegularFile(Path normalized) {
        try {
            if (!normalized.startsWith(uploadDir) || Files.isSymbolicLink(normalized)) {
                return null;
            }
            Path baseReal = uploadDir.toRealPath();
            Path fileReal = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!fileReal.startsWith(baseReal) || !Files.isRegularFile(fileReal, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            return fileReal;
        } catch (IOException e) {
            return null;
        }
    }

    private String localUrl(String localPath) {
        return uploadBaseUrl + localPath;
    }

    private static MediaType contentTypeFor(String filename) {
        if (filename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (filename.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    private static void requireSafeFilename(String filename) {
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        // 404 tambien en accesos no autorizados: no revela si el archivo existe
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
