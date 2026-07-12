package org.gipsybuho.recetasfamiliares.families;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.gipsybuho.recetasfamiliares.auth.AccountActionTokenService;
import org.gipsybuho.recetasfamiliares.auth.AccountActionTokenType;
import org.gipsybuho.recetasfamiliares.auth.AccountEmailService;
import org.gipsybuho.recetasfamiliares.auth.RefreshTokenService;
import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeRepository;
import org.gipsybuho.recetasfamiliares.menus.MenuItemRepository;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.recipes.StarterRecipeSeeder;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FamilyService {

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AccountActionTokenService accountActionTokenService;
    private final AccountEmailService accountEmailService;
    private final RecipeRepository recipeRepository;
    private final StockItemRepository stockItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final FamilyNoteRepository familyNoteRepository;
    private final FavoriteRecipeRepository favoriteRecipeRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.gipsybuho.recetasfamiliares.photos.FileStorageService fileStorageService;
    private final StarterRecipeSeeder starterRecipeSeeder;
    private final Duration passwordResetTokenTtl;
    private final Duration emailVerificationTokenTtl;

    public FamilyService(
            FamilyMemberRepository familyMemberRepository,
            FamilyRepository familyRepository,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            AccountActionTokenService accountActionTokenService,
            AccountEmailService accountEmailService,
            RecipeRepository recipeRepository,
            StockItemRepository stockItemRepository,
            MenuItemRepository menuItemRepository,
            ShoppingListRepository shoppingListRepository,
            FamilyNoteRepository familyNoteRepository,
            FavoriteRecipeRepository favoriteRecipeRepository,
            PasswordEncoder passwordEncoder,
            org.gipsybuho.recetasfamiliares.photos.FileStorageService fileStorageService,
            StarterRecipeSeeder starterRecipeSeeder,
            @Value("${app.account.password-reset-token-ttl-minutes:30}") long passwordResetTokenTtlMinutes,
            @Value("${app.account.email-verification-token-ttl-hours:24}") long emailVerificationTokenTtlHours
    ) {
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.accountActionTokenService = accountActionTokenService;
        this.accountEmailService = accountEmailService;
        this.recipeRepository = recipeRepository;
        this.stockItemRepository = stockItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.familyNoteRepository = familyNoteRepository;
        this.favoriteRecipeRepository = favoriteRecipeRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.starterRecipeSeeder = starterRecipeSeeder;
        this.passwordResetTokenTtl = Duration.ofMinutes(passwordResetTokenTtlMinutes);
        this.emailVerificationTokenTtl = Duration.ofHours(emailVerificationTokenTtlHours);
    }

    @Transactional(readOnly = true)
    public List<FamilyResponse> findFamiliesForUser(String userId) {
        return familyMemberRepository.findByUser_IdAndDeletedFalse(userId)
                .stream()
                .map(member -> new FamilyResponse(
                        member.getFamily().getId(),
                        member.getFamily().getName(),
                        member.getRole(),
                        member.getFamily().getAvatarUrl()
                ))
                .toList();
    }

    /** Tope defensivo de membresias activas por usuario: frena creacion masiva de familias. */
    private static final int MAX_ACTIVE_MEMBERSHIPS = 10;

    @Transactional
    public FamilyResponse createFamily(String userId, CreateFamilyRequest request) {
        List<FamilyMemberEntity> activeMemberships = familyMemberRepository.findByUser_IdAndDeletedFalse(userId);
        boolean canCreateAdditionalFamily = activeMemberships.isEmpty()
                || activeMemberships.stream()
                        .anyMatch(member -> member.getRole() == FamilyRole.OWNER || member.getRole() == FamilyRole.ADMIN);
        if (!canCreateAdditionalFamily) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family creation requires admin access");
        }
        if (activeMemberships.size() >= MAX_ACTIVE_MEMBERSHIPS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Family limit reached");
        }

        UserEntity user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Family creation requires admin access"));
        FamilyEntity family = familyRepository.save(new FamilyEntity(request.name().trim()));
        familyMemberRepository.save(new FamilyMemberEntity(family, user, FamilyRole.OWNER));
        starterRecipeSeeder.seedStarterRecipes(family);
        return new FamilyResponse(family.getId(), family.getName(), FamilyRole.OWNER, family.getAvatarUrl());
    }

    @Transactional(readOnly = true)
    public List<FamilyMemberResponse> listMembers(String familyId, String callerUserId) {
        requireMembership(familyId, callerUserId);
        return familyMemberRepository.findMembersWithUserByFamilyId(familyId)
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public FamilyMemberResponse updateMemberRole(String familyId, String targetUserId,
            String callerUserId, UpdateMemberRoleRequest request) {
        requireAdminOrAbove(familyId, callerUserId);
        FamilyRole newRole = parseRole(request.role());
        if (newRole == FamilyRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign OWNER role");
        }
        if (targetUserId.equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change your own role");
        }
        FamilyMemberEntity target = requireActiveMember(familyId, targetUserId);
        if (target.getRole() == FamilyRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change OWNER role");
        }
        target.setRole(newRole);
        return toMemberResponse(familyMemberRepository.save(target));
    }

    @Transactional
    public FamilyMemberResponse updateMember(String familyId, String targetUserId,
            String callerUserId, UpdateFamilyMemberRequest request) {
        requireAdminOrAbove(familyId, callerUserId);
        if (targetUserId.equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot edit yourself as a family member");
        }

        FamilyMemberEntity target = requireActiveMember(familyId, targetUserId);
        if (target.getRole() == FamilyRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot edit OWNER account");
        }

        UserEntity user = target.getUser();
        String displayName = request.displayName().trim();
        if (displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
        }
        boolean belongsToMultipleFamilies = familyMemberRepository
                .findByUser_IdAndDeletedFalse(targetUserId).size() > 1;

        String email = normalizeEmail(request.email());
        boolean revokeSessions = false;
        boolean emailChanged = !email.equalsIgnoreCase(user.getEmail());
        if (emailChanged) {
            if (belongsToMultipleFamilies) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot change this member's email directly; send a password reset email instead");
            }
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
            }
            user.changeEmail(email);
            revokeSessions = true;
        }

        user.setDisplayName(displayName);

        UpdateFamilyMemberRequest.PasswordAction passwordAction = request.passwordAction() != null
                ? request.passwordAction()
                : UpdateFamilyMemberRequest.PasswordAction.NONE;
        String temporaryPassword = request.temporaryPassword();
        boolean sendPasswordReset = false;
        switch (passwordAction) {
            case NONE -> {
                if (temporaryPassword != null && !temporaryPassword.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password action is required");
                }
            }
            case SEND_RESET -> {
                if (temporaryPassword != null && !temporaryPassword.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Temporary password is not allowed for reset email");
                }
                requireEmailDeliveryConfigured();
                sendPasswordReset = true;
            }
            case SET_TEMPORARY -> {
                if (temporaryPassword == null || temporaryPassword.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Temporary password is required");
                }
                if (belongsToMultipleFamilies) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot set a temporary password for this member directly; send a password reset email instead");
                }
                user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
                revokeSessions = true;
            }
        }

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered", ex);
        }
        if (revokeSessions) {
            refreshTokenService.revokeAllForUser(user.getId());
        }
        String emailVerificationToken = emailChanged ? issueEmailVerificationTokenIfEnabled(user) : null;
        String passwordResetToken = sendPasswordReset ? issuePasswordResetToken(user) : null;
        if (emailVerificationToken != null) {
            accountEmailService.sendEmailVerification(user, emailVerificationToken);
        }
        if (passwordResetToken != null) {
            accountEmailService.sendPasswordReset(user, passwordResetToken);
        }
        return toMemberResponse(target);
    }

    @Transactional
    public void removeMember(String familyId, String targetUserId, String callerUserId) {
        requireAdminOrAbove(familyId, callerUserId);
        if (targetUserId.equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove yourself from family");
        }
        FamilyMemberEntity target = requireActiveMember(familyId, targetUserId);
        if (target.getRole() == FamilyRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove OWNER from family");
        }
        target.softDelete();
        familyMemberRepository.save(target);
        refreshTokenService.revokeAllForUser(targetUserId);
    }

    /**
     * Imagen del grupo familiar (punto 15 del roadmap). Solo OWNER/ADMIN pueden
     * cambiarla; la validacion real del archivo vive en FileStorageService
     * (allowlist, magic bytes, re-encode) igual que el avatar de usuario.
     */
    @Transactional
    public FamilyResponse uploadAvatar(String familyId, String callerUserId,
            org.springframework.web.multipart.MultipartFile file) {
        requireAdminOrAbove(familyId, callerUserId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        try {
            var stored = fileStorageService.store(file, "family_avatars");
            family.setAvatarUrl(stored.url());
            FamilyEntity saved = familyRepository.save(family);
            FamilyRole callerRole = familyMemberRepository
                    .findMemberWithUserByFamilyIdAndUserId(familyId, callerUserId)
                    .map(FamilyMemberEntity::getRole)
                    .orElse(null);
            return new FamilyResponse(saved.getId(), saved.getName(), callerRole, saved.getAvatarUrl());
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la imagen");
        }
    }

    @Transactional
    public void inviteMember(String familyId, String callerUserId, InviteMemberRequest request) {
        requireAdminOrAbove(familyId, callerUserId);
        FamilyRole role = parseRole(request.role());
        if (role == FamilyRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign OWNER role");
        }
        String email = normalizeEmail(request.email());
        UserEntity invitedUser = userRepository.findByEmailIgnoreCaseAndDeletedFalse(email)
                .orElseGet(() -> createUserForInvite(email, request));
        if (invitedUser == null) {
            return; // Anti-enumeration: inviting an unknown email without creation data is a silent no-op.
        }
        if (familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, invitedUser.getId())) {
            return; // Already a member — silent no-op (OWASP A01: full anti-enumeration).
        }
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        familyMemberRepository.save(new FamilyMemberEntity(family, invitedUser, role));
    }

    @Transactional(readOnly = true)
    public FamilyStatsResponse getFamilyStats(String familyId, String userId) {
        requireMembership(familyId, userId);
        long totalRecipes    = recipeRepository.countByFamily_IdAndDeletedFalse(familyId);
        long totalMembers    = familyMemberRepository.countByFamily_IdAndDeletedFalse(familyId);
        long totalStockItems = stockItemRepository.countByFamily_IdAndDeletedFalse(familyId);
        // COD-4: actividad real de la familia, no solo recetas. Ingredientes, pasos y
        // fotos tocan la receta; los items de lista tocan su lista: quedan cubiertos.
        Instant lastActivity = java.util.stream.Stream.of(
                        recipeRepository.findLastActivityAt(familyId),
                        stockItemRepository.findLastActivityAt(familyId),
                        menuItemRepository.findLastActivityAt(familyId),
                        shoppingListRepository.findLastActivityAt(familyId),
                        familyNoteRepository.findLastActivityAt(familyId),
                        favoriteRecipeRepository.findLastActivityAt(familyId))
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        return new FamilyStatsResponse(totalRecipes, totalMembers, totalStockItems, lastActivity);
    }

    private void requireMembership(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
    }

    private void requireAdminOrAbove(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                familyId, userId, List.of(FamilyRole.OWNER, FamilyRole.ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private FamilyMemberEntity requireActiveMember(String familyId, String userId) {
        return familyMemberRepository.findMemberWithUserByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    private FamilyRole parseRole(String roleStr) {
        try {
            return FamilyRole.valueOf(roleStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // No reflejar el input del cliente en el mensaje de error (COD-10).
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        }
    }

    private UserEntity createUserForInvite(String email, InviteMemberRequest request) {
        boolean hasDisplayName = request.displayName() != null && !request.displayName().isBlank();
        boolean hasPassword = request.password() != null && !request.password().isBlank();
        if (!hasDisplayName && !hasPassword) {
            return null;
        }
        if (!hasDisplayName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required for new users");
        }
        if (!hasPassword || request.password().length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 12 characters");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        try {
            return userRepository.saveAndFlush(new UserEntity(
                    email,
                    request.displayName().trim(),
                    passwordEncoder.encode(request.password())
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered", ex);
        }
    }

    private String issuePasswordResetToken(UserEntity user) {
        requireEmailDeliveryConfigured();
        return accountActionTokenService.issue(
                user,
                AccountActionTokenType.PASSWORD_RESET,
                passwordResetTokenTtl
        ).rawToken();
    }

    private void requireEmailDeliveryConfigured() {
        if (!accountEmailService.isDeliveryEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured");
        }
    }

    private String issueEmailVerificationTokenIfEnabled(UserEntity user) {
        if (!accountEmailService.isDeliveryEnabled()) {
            return null;
        }
        return accountActionTokenService.issue(
                user,
                AccountActionTokenType.EMAIL_VERIFICATION,
                emailVerificationTokenTtl
        ).rawToken();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private FamilyMemberResponse toMemberResponse(FamilyMemberEntity member) {
        UserEntity user = member.getUser();
        return new FamilyMemberResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                member.getRole().name()
        );
    }
}
