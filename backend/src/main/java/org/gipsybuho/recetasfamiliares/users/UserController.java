package org.gipsybuho.recetasfamiliares.users;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getMe(Authentication authentication) {
        return userService.getUser(authentication.getName());
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateUserRequest request,
                                 Authentication authentication) {
        return userService.updateUser(authentication.getName(), request);
    }
}
