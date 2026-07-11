package org.gipsybuho.recetasfamiliares.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.security.JwtService;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AccountActionTokenService accountActionTokenService;
    @Mock
    private AccountEmailService accountEmailService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userRepository,
                familyRepository,
                familyMemberRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                accountActionTokenService,
                accountEmailService,
                30,
                24
        );
    }

    @Test
    void passwordResetRequestIssuesTokenOnlyWhenMailIsEnabledAndUserExists() {
        UserEntity user = user("user-1", "ana@example.com", "Ana", "hash");
        when(userRepository.findByEmailIgnoreCaseAndDeletedFalse("ana@example.com")).thenReturn(Optional.of(user));
        when(accountEmailService.isDeliveryEnabled()).thenReturn(true);
        when(accountActionTokenService.issue(
                user,
                AccountActionTokenType.PASSWORD_RESET,
                Duration.ofMinutes(30)
        )).thenReturn(new AccountActionTokenService.IssuedAccountToken("raw-token"));

        service.requestPasswordReset(new PasswordResetRequest(" ANA@example.com "));

        verify(accountEmailService).sendPasswordReset(user, "raw-token");
    }

    @Test
    void passwordResetRequestDoesNotEnumerateUnknownEmails() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedFalse("missing@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset(new PasswordResetRequest("missing@example.com"));

        verifyNoInteractions(accountActionTokenService);
        verify(accountEmailService, never()).sendPasswordReset(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void confirmPasswordResetChangesPasswordAndRevokesSessions() {
        UserEntity user = user("user-1", "ana@example.com", "Ana", "old-hash");
        when(accountActionTokenService.consume("reset-token", AccountActionTokenType.PASSWORD_RESET))
                .thenReturn(user);
        when(passwordEncoder.encode("new-secure-password")).thenReturn("new-hash");

        service.confirmPasswordReset(new ConfirmPasswordResetRequest("reset-token", "new-secure-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser("user-1");
    }

    @Test
    void emailVerificationRequestSkipsAlreadyVerifiedUsers() {
        UserEntity user = user("user-1", "ana@example.com", "Ana", "hash");
        user.markEmailVerified();
        when(userRepository.findByEmailIgnoreCaseAndDeletedFalse("ana@example.com")).thenReturn(Optional.of(user));

        service.requestEmailVerification(new EmailVerificationRequest("ana@example.com"));

        verifyNoInteractions(accountActionTokenService);
        verify(accountEmailService, never()).sendEmailVerification(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void confirmEmailVerificationMarksUserVerified() {
        UserEntity user = user("user-1", "ana@example.com", "Ana", "hash");
        when(accountActionTokenService.consume("verify-token", AccountActionTokenType.EMAIL_VERIFICATION))
                .thenReturn(user);

        service.confirmEmailVerification(new ConfirmEmailVerificationRequest("verify-token"));

        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void deleteAccountRejectsWrongPassword() {
        UserEntity user = user("user-1", "ana@example.com", "Ana", "hash");
        when(userRepository.findByIdAndDeletedFalse("user-1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-password", "hash")).thenReturn(false);

        // 403 y no 401: un 401 haria que el authenticator OkHttp de los clientes
        // refresque el token, reintente y acabe limpiando su sesion local
        assertThatThrownBy(() -> service.deleteAccount("user-1", new DeleteAccountRequest("bad-password")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatusCode().value()).isEqualTo(403));

        verify(refreshTokenService, never()).revokeAllForUser(anyString());
        verify(userRepository, never()).save(user);
    }

    @Test
    void deleteAccountAnonymizesUserAndPromotesReplacementOwner() {
        UserEntity owner = user("owner-1", "owner@example.com", "Owner", "hash");
        UserEntity admin = user("admin-1", "admin@example.com", "Admin", "hash");
        FamilyEntity family = family("family-1");
        FamilyMemberEntity ownerMembership = member("member-owner", family, owner, FamilyRole.OWNER, Instant.parse("2026-01-01T00:00:00Z"));
        FamilyMemberEntity adminMembership = member("member-admin", family, admin, FamilyRole.ADMIN, Instant.parse("2026-01-02T00:00:00Z"));

        when(userRepository.findByIdAndDeletedFalse("owner-1")).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("current-password", "hash")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("deleted-hash");
        when(familyMemberRepository.findByUser_IdAndDeletedFalse("owner-1")).thenReturn(List.of(ownerMembership));
        when(familyMemberRepository.findMembersWithUserByFamilyId("family-1"))
                .thenReturn(List.of(ownerMembership, adminMembership));

        service.deleteAccount("owner-1", new DeleteAccountRequest("current-password"));

        assertThat(adminMembership.getRole()).isEqualTo(FamilyRole.OWNER);
        assertThat(ownerMembership.isDeleted()).isTrue();
        assertThat(owner.isDeleted()).isTrue();
        assertThat(owner.getEmail()).isEqualTo("deleted+owner-1@deleted.recetas.local");
        assertThat(owner.getDisplayName()).isEqualTo("Cuenta eliminada");
        assertThat(owner.getPasswordHash()).isEqualTo("deleted-hash");
        verify(refreshTokenService).revokeAllForUser("owner-1");
        verify(userRepository).save(owner);
    }

    private static UserEntity user(String id, String email, String displayName, String passwordHash) {
        UserEntity user = new UserEntity(email, displayName, passwordHash);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static FamilyEntity family(String id) {
        FamilyEntity family = new FamilyEntity("Familia Test");
        ReflectionTestUtils.setField(family, "id", id);
        return family;
    }

    private static FamilyMemberEntity member(
            String id,
            FamilyEntity family,
            UserEntity user,
            FamilyRole role,
            Instant createdAt
    ) {
        FamilyMemberEntity member = new FamilyMemberEntity(family, user, role);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", createdAt);
        return member;
    }
}
