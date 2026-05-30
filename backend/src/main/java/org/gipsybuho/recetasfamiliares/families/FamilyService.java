package org.gipsybuho.recetasfamiliares.families;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.gipsybuho.recetasfamiliares.auth.RefreshTokenService;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.http.HttpStatus;
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

    public FamilyService(
            FamilyMemberRepository familyMemberRepository,
            FamilyRepository familyRepository,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            RecipeRepository recipeRepository,
            StockItemRepository stockItemRepository
    ) {
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.recipeRepository = recipeRepository;
        this.stockItemRepository = stockItemRepository;
    }

    @Transactional(readOnly = true)
    public List<FamilyResponse> findFamiliesForUser(String userId) {
        return familyMemberRepository.findByUser_IdAndDeletedFalse(userId)
                .stream()
                .map(member -> new FamilyResponse(
                        member.getFamily().getId(),
                        member.getFamily().getName(),
                        member.getRole()
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

    @Transactional
    public void inviteMember(String familyId, String callerUserId, InviteMemberRequest request) {
        requireAdminOrAbove(familyId, callerUserId);
        FamilyRole role = parseRole(request.role());
        if (role == FamilyRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign OWNER role");
        }
        userRepository.findByEmailIgnoreCaseAndDeletedFalse(request.email()).ifPresent(invitedUser -> {
            if (familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, invitedUser.getId())) {
                return; // Already a member — silent no-op (OWASP A01: full anti-enumeration)
            }
            FamilyEntity family = familyRepository.findById(familyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
            familyMemberRepository.save(new FamilyMemberEntity(family, invitedUser, role));
        });
    }

    @Transactional(readOnly = true)
    public FamilyStatsResponse getFamilyStats(String familyId, String userId) {
        requireMembership(familyId, userId);
        long totalRecipes    = recipeRepository.countByFamily_IdAndDeletedFalse(familyId);
        long totalMembers    = familyMemberRepository.countByFamily_IdAndDeletedFalse(familyId);
        long totalStockItems = stockItemRepository.countByFamily_IdAndDeletedFalse(familyId);
        Instant lastActivity = recipeRepository
                .findFirstByFamily_IdAndDeletedFalseOrderByUpdatedAtDesc(familyId)
                .map(RecipeEntity::getUpdatedAt)
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + roleStr);
        }
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
