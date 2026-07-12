package org.gipsybuho.recetasfamiliares.families;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.gipsybuho.recetasfamiliares.auth.AccountActionTokenService;
import org.gipsybuho.recetasfamiliares.auth.AccountActionTokenType;
import org.gipsybuho.recetasfamiliares.auth.AccountEmailService;
import org.gipsybuho.recetasfamiliares.auth.RefreshTokenService;
import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeRepository;
import org.gipsybuho.recetasfamiliares.menus.MenuItemRepository;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteRepository;
import org.gipsybuho.recetasfamiliares.photos.FileStorageService;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.recipes.StarterRecipeSeeder;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AccountActionTokenService accountActionTokenService;
    @Mock
    private AccountEmailService accountEmailService;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private StockItemRepository stockItemRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private ShoppingListRepository shoppingListRepository;
    @Mock
    private FamilyNoteRepository familyNoteRepository;
    @Mock
    private FavoriteRecipeRepository favoriteRecipeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StarterRecipeSeeder starterRecipeSeeder;

    private FamilyService service;

    @BeforeEach
    void setUp() {
        service = new FamilyService(
                familyMemberRepository,
                familyRepository,
                userRepository,
                refreshTokenService,
                accountActionTokenService,
                accountEmailService,
                recipeRepository,
                stockItemRepository,
                menuItemRepository,
                shoppingListRepository,
                familyNoteRepository,
                favoriteRecipeRepository,
                passwordEncoder,
                fileStorageService,
                starterRecipeSeeder,
                30,
                24
        );
    }

    @Test
    void createFamilyAllowsUserWithoutActiveFamily() {
        UserEntity owner = user("user-1");
        when(familyMemberRepository.findByUser_IdAndDeletedFalse("user-1")).thenReturn(List.of());
        when(userRepository.findByIdAndDeletedFalse("user-1")).thenReturn(Optional.of(owner));
        when(familyRepository.save(any(FamilyEntity.class))).thenAnswer(invocation -> {
            FamilyEntity family = invocation.getArgument(0);
            ReflectionTestUtils.setField(family, "id", "family-new");
            return family;
        });

        FamilyResponse response = service.createFamily("user-1", new CreateFamilyRequest(" Nueva familia "));

        assertThat(response.id()).isEqualTo("family-new");
        assertThat(response.name()).isEqualTo("Nueva familia");
        assertThat(response.role()).isEqualTo(FamilyRole.OWNER);
        verify(familyMemberRepository).save(argThat(member ->
                member.getFamily().getId().equals("family-new")
                        && member.getUser() == owner
                        && member.getRole() == FamilyRole.OWNER));
        verify(starterRecipeSeeder).seedStarterRecipes(argThat(family -> family.getId().equals("family-new")));
    }

    @Test
    void createFamilyAllowsAdminOrOwnerMembership() {
        UserEntity owner = user("user-1");
        FamilyEntity existingFamily = family("existing-family");
        FamilyMemberEntity existingMembership = new FamilyMemberEntity(existingFamily, owner, FamilyRole.ADMIN);
        when(familyMemberRepository.findByUser_IdAndDeletedFalse("user-1"))
                .thenReturn(List.of(existingMembership));
        when(userRepository.findByIdAndDeletedFalse("user-1")).thenReturn(Optional.of(owner));
        when(familyRepository.save(any(FamilyEntity.class))).thenAnswer(invocation -> {
            FamilyEntity family = invocation.getArgument(0);
            ReflectionTestUtils.setField(family, "id", "family-admin");
            return family;
        });

        FamilyResponse response = service.createFamily("user-1", new CreateFamilyRequest("Familia admin"));

        assertThat(response.id()).isEqualTo("family-admin");
        verify(familyMemberRepository).save(any(FamilyMemberEntity.class));
        verify(starterRecipeSeeder).seedStarterRecipes(any(FamilyEntity.class));
    }

    @Test
    void createFamilyRejectsMemberOnlyUser() {
        UserEntity member = user("member-1");
        FamilyEntity existingFamily = family("existing-family");
        FamilyMemberEntity existingMembership = new FamilyMemberEntity(existingFamily, member, FamilyRole.MEMBER);
        when(familyMemberRepository.findByUser_IdAndDeletedFalse("member-1"))
                .thenReturn(List.of(existingMembership));

        assertThatThrownBy(() -> service.createFamily("member-1", new CreateFamilyRequest("No permitida")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(familyRepository, never()).save(any(FamilyEntity.class));
        verify(familyMemberRepository, never()).save(any(FamilyMemberEntity.class));
        verify(starterRecipeSeeder, never()).seedStarterRecipes(any(FamilyEntity.class));
    }

    @Test
    void createFamilyRejectsWhenMembershipLimitReached() {
        UserEntity owner = user("user-1");
        List<FamilyMemberEntity> memberships = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new FamilyMemberEntity(family("family-" + i), owner, FamilyRole.OWNER))
                .toList();
        when(familyMemberRepository.findByUser_IdAndDeletedFalse("user-1")).thenReturn(memberships);

        assertThatThrownBy(() -> service.createFamily("user-1", new CreateFamilyRequest("Una mas")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(familyRepository, never()).save(any(FamilyEntity.class));
        verify(starterRecipeSeeder, never()).seedStarterRecipes(any(FamilyEntity.class));
    }

    @Test
    void updateMemberWithTemporaryPasswordUpdatesUserAndRevokesSessions() {
        UserEntity member = user("member-1");
        FamilyMemberEntity membership = activeMember(member, FamilyRole.MEMBER);
        mockAdminEditAccess(membership);
        when(passwordEncoder.encode("temporary-pass")).thenReturn("encoded-temp");
        when(userRepository.saveAndFlush(member)).thenReturn(member);

        FamilyMemberResponse response = service.updateMember(
                "family-1",
                "member-1",
                "owner-1",
                new UpdateFamilyMemberRequest(
                        " Nuevo nombre ",
                        " member-1@example.com ",
                        UpdateFamilyMemberRequest.PasswordAction.SET_TEMPORARY,
                        "temporary-pass"
                )
        );

        assertThat(response.displayName()).isEqualTo("Nuevo nombre");
        assertThat(member.getDisplayName()).isEqualTo("Nuevo nombre");
        assertThat(member.getPasswordHash()).isEqualTo("encoded-temp");
        verify(refreshTokenService).revokeAllForUser("member-1");
        verify(accountEmailService, never()).sendPasswordReset(any(UserEntity.class), any());
    }

    @Test
    void updateMemberWithResetEmailIssuesTokenWithoutRevokingSessions() {
        UserEntity member = user("member-1");
        FamilyMemberEntity membership = activeMember(member, FamilyRole.MEMBER);
        mockAdminEditAccess(membership);
        when(accountEmailService.isDeliveryEnabled()).thenReturn(true);
        when(accountActionTokenService.issue(
                eq(member),
                eq(AccountActionTokenType.PASSWORD_RESET),
                any(Duration.class)
        )).thenReturn(new AccountActionTokenService.IssuedAccountToken("raw-reset-token"));
        when(userRepository.saveAndFlush(member)).thenReturn(member);

        service.updateMember(
                "family-1",
                "member-1",
                "owner-1",
                new UpdateFamilyMemberRequest(
                        "Test User",
                        "member-1@example.com",
                        UpdateFamilyMemberRequest.PasswordAction.SEND_RESET,
                        null
                )
        );

        verify(accountEmailService).sendPasswordReset(member, "raw-reset-token");
        verify(refreshTokenService, never()).revokeAllForUser("member-1");
    }

    @Test
    void updateMemberEmailClearsVerificationAndRevokesSessions() {
        UserEntity member = user("member-1");
        member.markEmailVerified();
        FamilyMemberEntity membership = activeMember(member, FamilyRole.MEMBER);
        mockAdminEditAccess(membership);
        when(userRepository.existsByEmailIgnoreCase("nuevo@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(member)).thenReturn(member);

        FamilyMemberResponse response = service.updateMember(
                "family-1",
                "member-1",
                "owner-1",
                new UpdateFamilyMemberRequest(
                        "Test User",
                        "nuevo@example.com",
                        UpdateFamilyMemberRequest.PasswordAction.NONE,
                        null
                )
        );

        assertThat(response.email()).isEqualTo("nuevo@example.com");
        assertThat(member.isEmailVerified()).isFalse();
        verify(refreshTokenService).revokeAllForUser("member-1");
    }

    @Test
    void updateMemberBlocksTemporaryPasswordWhenTargetBelongsToMultipleFamilies() {
        UserEntity member = user("member-1");
        FamilyMemberEntity membership = activeMember(member, FamilyRole.MEMBER);
        mockAdminEditAccess(membership);
        when(familyMemberRepository.findByUser_IdAndDeletedFalse("member-1"))
                .thenReturn(List.of(membership, new FamilyMemberEntity(family("family-2"), member, FamilyRole.MEMBER)));

        assertThatThrownBy(() -> service.updateMember(
                "family-1",
                "member-1",
                "owner-1",
                new UpdateFamilyMemberRequest(
                        "Test User",
                        "member-1@example.com",
                        UpdateFamilyMemberRequest.PasswordAction.SET_TEMPORARY,
                        "temporary-pass"
                )
        )).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void updateMemberBlocksEmailChangeWhenTargetBelongsToMultipleFamilies() {
        UserEntity member = user("member-1");
        FamilyMemberEntity membership = activeMember(member, FamilyRole.MEMBER);
        mockAdminEditAccess(membership);
        when(familyMemberRepository.findByUser_IdAndDeletedFalse("member-1"))
                .thenReturn(List.of(membership, new FamilyMemberEntity(family("family-2"), member, FamilyRole.MEMBER)));

        assertThatThrownBy(() -> service.updateMember(
                "family-1",
                "member-1",
                "owner-1",
                new UpdateFamilyMemberRequest(
                        "Test User",
                        "nuevo@example.com",
                        UpdateFamilyMemberRequest.PasswordAction.NONE,
                        null
                )
        )).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    @Test
    void updateMemberAllowsResetEmailWhenTargetBelongsToMultipleFamilies() {
        UserEntity member = user("member-1");
        FamilyMemberEntity membership = activeMember(member, FamilyRole.MEMBER);
        mockAdminEditAccess(membership);
        when(familyMemberRepository.findByUser_IdAndDeletedFalse("member-1"))
                .thenReturn(List.of(membership, new FamilyMemberEntity(family("family-2"), member, FamilyRole.MEMBER)));
        when(accountEmailService.isDeliveryEnabled()).thenReturn(true);
        when(accountActionTokenService.issue(
                eq(member),
                eq(AccountActionTokenType.PASSWORD_RESET),
                any(Duration.class)
        )).thenReturn(new AccountActionTokenService.IssuedAccountToken("raw-reset-token"));
        when(userRepository.saveAndFlush(member)).thenReturn(member);

        service.updateMember(
                "family-1",
                "member-1",
                "owner-1",
                new UpdateFamilyMemberRequest(
                        "Test User",
                        "member-1@example.com",
                        UpdateFamilyMemberRequest.PasswordAction.SEND_RESET,
                        null
                )
        );

        verify(accountEmailService).sendPasswordReset(member, "raw-reset-token");
    }

    private static UserEntity user(String id) {
        UserEntity user = new UserEntity(id + "@example.com", "Test User", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static FamilyEntity family(String id) {
        FamilyEntity family = new FamilyEntity("Familia");
        ReflectionTestUtils.setField(family, "id", id);
        return family;
    }

    private static FamilyMemberEntity activeMember(UserEntity user, FamilyRole role) {
        return new FamilyMemberEntity(family("family-1"), user, role);
    }

    private void mockAdminEditAccess(FamilyMemberEntity target) {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                "family-1",
                "owner-1",
                List.of(FamilyRole.OWNER, FamilyRole.ADMIN)
        )).thenReturn(true);
        when(familyMemberRepository.findMemberWithUserByFamilyIdAndUserId("family-1", target.getUser().getId()))
                .thenReturn(Optional.of(target));
    }
}
