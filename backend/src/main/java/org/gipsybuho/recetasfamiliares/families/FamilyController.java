package org.gipsybuho.recetasfamiliares.families;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @GetMapping
    public List<FamilyResponse> listMyFamilies(Authentication authentication) {
        return familyService.findFamiliesForUser(authentication.getName());
    }

    @PostMapping("/{familyId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void inviteMember(
            @PathVariable String familyId,
            @Valid @RequestBody InviteMemberRequest request,
            Authentication authentication
    ) {
        familyService.inviteMember(familyId, authentication.getName(), request);
    }

    @GetMapping("/{familyId}/members")
    public List<FamilyMemberResponse> listMembers(
            @PathVariable String familyId,
            Authentication authentication
    ) {
        return familyService.listMembers(familyId, authentication.getName());
    }

    @PutMapping("/{familyId}/members/{userId}/role")
    public FamilyMemberResponse updateMemberRole(
            @PathVariable String familyId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            Authentication authentication
    ) {
        return familyService.updateMemberRole(familyId, userId, authentication.getName(), request);
    }

    @DeleteMapping("/{familyId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable String familyId,
            @PathVariable String userId,
            Authentication authentication
    ) {
        familyService.removeMember(familyId, userId, authentication.getName());
    }

    @PostMapping("/{familyId}/avatar")
    public FamilyResponse uploadAvatar(
            @PathVariable String familyId,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            Authentication authentication
    ) {
        return familyService.uploadAvatar(familyId, authentication.getName(), file);
    }

    @GetMapping("/{familyId}/stats")
    public FamilyStatsResponse getFamilyStats(
            @PathVariable String familyId,
            Authentication authentication
    ) {
        return familyService.getFamilyStats(familyId, authentication.getName());
    }
}
