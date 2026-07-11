package org.gipsybuho.recetasfamiliares.families;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.gipsybuho.recetasfamiliares.auth.RefreshTokenService;
import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeRepository;
import org.gipsybuho.recetasfamiliares.menus.MenuItemRepository;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final RecipeRepository recipeRepository;
    private final StockItemRepository stockItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final FamilyNoteRepository familyNoteRepository;
    private final FavoriteRecipeRepository favoriteRecipeRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.gipsybuho.recetasfamiliares.photos.FileStorageService fileStorageService;

    public FamilyService(
            FamilyMemberRepository familyMemberRepository,
            FamilyRepository familyRepository,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            RecipeRepository recipeRepository,
            StockItemRepository stockItemRepository,
            MenuItemRepository menuItemRepository,
            ShoppingListRepository shoppingListRepository,
            FamilyNoteRepository familyNoteRepository,
            FavoriteRecipeRepository favoriteRecipeRepository,
            PasswordEncoder passwordEncoder,
            org.gipsybuho.recetasfamiliares.photos.FileStorageService fileStorageService
    ) {
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.recipeRepository = recipeRepository;
        this.stockItemRepository = stockItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.familyNoteRepository = familyNoteRepository;
        this.favoriteRecipeRepository = favoriteRecipeRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
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
