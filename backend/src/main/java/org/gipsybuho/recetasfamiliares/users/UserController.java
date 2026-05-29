package org.gipsybuho.recetasfamiliares.users;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse uploadAvatar(@RequestPart("file") MultipartFile file,
                                     Authentication authentication) {
        return userService.uploadAvatar(authentication.getName(), file);
    }
}
