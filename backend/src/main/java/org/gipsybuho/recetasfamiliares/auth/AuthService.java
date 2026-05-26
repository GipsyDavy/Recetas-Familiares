package org.gipsybuho.recetasfamiliares.auth;

import java.util.Locale;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.security.JwtService;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "Email is already registered");
        }

        UserEntity user = userRepository.save(new UserEntity(
                email,
                request.displayName().trim(),
                passwordEncoder.encode(request.password())
        ));
        FamilyEntity family = familyRepository.save(new FamilyEntity(request.familyName().trim()));
        familyMemberRepository.save(new FamilyMemberEntity(family, user, FamilyRole.OWNER));

        return issueAuthResponse(user, family);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalizeEmail(request.email()))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        FamilyEntity family = requirePrimaryFamily(user);
        return issueAuthResponse(user, family);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenEntity currentToken = refreshTokenService.requireActive(request.refreshToken());
        RefreshTokenService.IssuedRefreshToken replacement = refreshTokenService.rotate(currentToken);
        UserEntity user = currentToken.getUser();
        FamilyEntity family = requirePrimaryFamily(user);
        return buildAuthResponse(user, family, replacement.rawToken());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponse issueAuthResponse(UserEntity user, FamilyEntity family) {
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);
        return buildAuthResponse(user, family, refreshToken.rawToken());
    }

    private AuthResponse buildAuthResponse(UserEntity user, FamilyEntity family, String rawRefreshToken) {
        JwtService.IssuedAccessToken accessToken = jwtService.issue(user);
        return new AuthResponse(
                "Bearer",
                accessToken.token(),
                rawRefreshToken,
                accessToken.expiresInSeconds(),
                new AuthUserResponse(user.getId(), user.getEmail(), user.getDisplayName()),
                new AuthFamilyResponse(family.getId(), family.getName())
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private FamilyEntity requirePrimaryFamily(UserEntity user) {
        return familyMemberRepository.findFirstByUser_IdAndDeletedFalse(user.getId())
                .map(FamilyMemberEntity::getFamily)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Family not found"));
    }
}
