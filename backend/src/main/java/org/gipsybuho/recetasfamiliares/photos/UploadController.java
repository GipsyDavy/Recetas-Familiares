package org.gipsybuho.recetasfamiliares.photos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
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
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public UploadController(
            @Value("${app.upload.dir:./uploads}") String uploadDirPath,
            RecipePhotoRepository photoRepository,
            UserRepository userRepository,
            FamilyMemberRepository familyMemberRepository
    ) {
        this.uploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    @GetMapping("/uploads/{filename}")
    public ResponseEntity<byte[]> recipePhoto(@PathVariable String filename, Authentication authentication) {
        requireSafeFilename(filename);
        String userId = authentication.getName();
        List<String> owningFamilyIds = photoRepository.findOwningFamilyIdsByUrlSuffix("/uploads/" + filename);
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
        List<String> ownerIds = userRepository.findIdsByAvatarUrlSuffix("/uploads/avatars/" + filename);
        boolean allowed = ownerIds.contains(requesterId) || sharesFamilyWithAny(requesterId, ownerIds);
        if (!allowed) {
            throw notFound();
        }
        return serveFile(uploadDir.resolve("avatars").resolve(filename), filename);
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

    private ResponseEntity<byte[]> serveFile(Path file, String filename) {
        Path normalized = file.normalize();
        if (!normalized.startsWith(uploadDir) || !Files.isRegularFile(normalized)) {
            throw notFound();
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(normalized);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
        return ResponseEntity.ok()
                .contentType(contentTypeFor(filename))
                .header("Cache-Control", "private, max-age=86400")
                .body(bytes);
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
}
