package org.gipsybuho.recetasfamiliares.users;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getUser(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCaseAndDeletedFalse(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(String email, UpdateUserRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCaseAndDeletedFalse(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.setDisplayName(request.displayName().trim());
        return toResponse(userRepository.save(user));
    }

    private static UserResponse toResponse(UserEntity u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName());
    }
}
