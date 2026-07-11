package org.gipsybuho.recetasfamiliares.users;

import java.io.IOException;

import org.gipsybuho.recetasfamiliares.photos.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public UserService(UserRepository userRepository, FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    public UserResponse getUser(String userId) {
        UserEntity user = findActiveUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(String userId, UpdateUserRequest request) {
        UserEntity user = findActiveUser(userId);
        user.setDisplayName(request.displayName().trim());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse uploadAvatar(String userId, MultipartFile file) {
        UserEntity user = findActiveUser(userId);
        try {
            FileStorageService.StoredFile stored = fileStorageService.store(file, "avatars");
            user.setAvatarUrl(stored.url());
            return toResponse(userRepository.save(user));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la imagen");
        }
    }

    /** El principal JWT es el userId (subject), no el email. */
    private UserEntity findActiveUser(String userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static UserResponse toResponse(UserEntity u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.isEmailVerified());
    }
}
