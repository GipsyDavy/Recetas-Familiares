package org.gipsybuho.recetasfamiliares.auth;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.recipes.StarterRecipeSeeder;
import org.gipsybuho.recetasfamiliares.security.JwtService;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
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
    private final AccountActionTokenService accountActionTokenService;
    private final AccountEmailService accountEmailService;
    private final StarterRecipeSeeder starterRecipeSeeder;
    private final Duration passwordResetTokenTtl;
    private final Duration emailVerificationTokenTtl;

    public AuthService(
            UserRepository userRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AccountActionTokenService accountActionTokenService,
            AccountEmailService accountEmailService,
            StarterRecipeSeeder starterRecipeSeeder,
            @Value("${app.account.password-reset-token-ttl-minutes:30}") long passwordResetTokenTtlMinutes,
            @Value("${app.account.email-verification-token-ttl-hours:24}") long emailVerificationTokenTtlHours
    ) {
        this.userRepository = userRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.accountActionTokenService = accountActionTokenService;
        this.accountEmailService = accountEmailService;
        this.starterRecipeSeeder = starterRecipeSeeder;
        this.passwordResetTokenTtl = Duration.ofMinutes(passwordResetTokenTtlMinutes);
        this.emailVerificationTokenTtl = Duration.ofHours(emailVerificationTokenTtlHours);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "Email is already registered");
        }

        UserEntity user;
        try {
            // saveAndFlush fuerza la violacion del unique de email aqui y no en
            // el commit, para que una carrera con otro registro simultaneo del
            // mismo email devuelva el mismo 409 que el check previo y no un 500.
            user = userRepository.saveAndFlush(new UserEntity(
                    email,
                    request.displayName().trim(),
                    passwordEncoder.encode(request.password())
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "Email is already registered");
        }
        FamilyEntity family = familyRepository.save(new FamilyEntity(request.familyName().trim()));
        familyMemberRepository.save(new FamilyMemberEntity(family, user, FamilyRole.OWNER));
        starterRecipeSeeder.seedStarterRecipes(family);
        sendEmailVerificationIfEnabled(user);

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

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalizeEmail(request.email()))
                .ifPresent(user -> {
                    if (accountEmailService.isDeliveryEnabled()) {
                        AccountActionTokenService.IssuedAccountToken token = accountActionTokenService.issue(
                                user,
                                AccountActionTokenType.PASSWORD_RESET,
                                passwordResetTokenTtl
                        );
                        accountEmailService.sendPasswordReset(user, token.rawToken());
                    }
                });
    }

    @Transactional
    public void confirmPasswordReset(ConfirmPasswordResetRequest request) {
        UserEntity user = accountActionTokenService.consume(
                request.token(),
                AccountActionTokenType.PASSWORD_RESET
        );
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Transactional
    public void requestEmailVerification(EmailVerificationRequest request) {
        userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalizeEmail(request.email()))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendEmailVerificationIfEnabled);
    }

    @Transactional
    public void confirmEmailVerification(ConfirmEmailVerificationRequest request) {
        UserEntity user = accountActionTokenService.consume(
                request.token(),
                AccountActionTokenType.EMAIL_VERIFICATION
        );
        user.markEmailVerified();
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(String userId, DeleteAccountRequest request) {
        UserEntity user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 403, no 401: el bearer es valido, y un 401 dispararia el refresh+retry
            // del authenticator OkHttp de los clientes, que acaba limpiando su sesion local
            throw new AuthException(HttpStatus.FORBIDDEN, "Invalid credentials");
        }

        List<FamilyMemberEntity> memberships = familyMemberRepository.findByUser_IdAndDeletedFalse(userId);
        memberships.forEach(membership -> removeMembershipForDeletedUser(membership, userId));
        refreshTokenService.revokeAllForUser(userId);
        user.softDeleteAnonymized(passwordEncoder.encode(UUID.randomUUID() + ":" + UUID.randomUUID()));
        userRepository.save(user);
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
                new AuthUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isEmailVerified()),
                new AuthFamilyResponse(family.getId(), family.getName())
        );
    }

    private void sendEmailVerificationIfEnabled(UserEntity user) {
        if (!accountEmailService.isDeliveryEnabled()) {
            return;
        }
        AccountActionTokenService.IssuedAccountToken token = accountActionTokenService.issue(
                user,
                AccountActionTokenType.EMAIL_VERIFICATION,
                emailVerificationTokenTtl
        );
        accountEmailService.sendEmailVerification(user, token.rawToken());
    }

    private void removeMembershipForDeletedUser(FamilyMemberEntity membership, String userId) {
        String familyId = membership.getFamily().getId();
        List<FamilyMemberEntity> activeMembers = familyMemberRepository.findMembersWithUserByFamilyId(familyId);
        List<FamilyMemberEntity> remainingMembers = activeMembers.stream()
                .filter(member -> !member.getUser().getId().equals(userId))
                .toList();

        if (remainingMembers.isEmpty()) {
            membership.getFamily().softDelete();
        } else if (membership.getRole() == FamilyRole.OWNER && remainingMembers.stream()
                .noneMatch(member -> member.getRole() == FamilyRole.OWNER)) {
            chooseReplacementOwner(remainingMembers).setRole(FamilyRole.OWNER);
        }

        membership.softDelete();
    }

    private FamilyMemberEntity chooseReplacementOwner(List<FamilyMemberEntity> remainingMembers) {
        return remainingMembers.stream()
                .min(Comparator
                        .comparingInt((FamilyMemberEntity member) -> member.getRole() == FamilyRole.ADMIN ? 0 : 1)
                        .thenComparing(FamilyMemberEntity::getCreatedAt))
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "Family has no replacement owner"));
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
