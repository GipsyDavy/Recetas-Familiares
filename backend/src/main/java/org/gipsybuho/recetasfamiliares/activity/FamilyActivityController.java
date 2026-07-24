package org.gipsybuho.recetasfamiliares.activity;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/families/{familyId}/activity")
public class FamilyActivityController {

    private final FamilyActivityService activityService;
    private final FamilyMemberRepository familyMemberRepository;

    public FamilyActivityController(
            FamilyActivityService activityService,
            FamilyMemberRepository familyMemberRepository
    ) {
        this.activityService = activityService;
        this.familyMemberRepository = familyMemberRepository;
    }

    @GetMapping
    public FamilyActivityResponse getActivity(@PathVariable String familyId, Authentication authentication) {
        String userId = authentication.getName();
        requireMembership(familyId, userId);
        return FamilyActivityResponse.from(activityService.unseenSections(familyId, userId));
    }

    @PostMapping("/{section}/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSeen(
            @PathVariable String familyId,
            @PathVariable String section,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        requireMembership(familyId, userId);
        FamilySection parsed;
        try {
            parsed = FamilySection.valueOf(section.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown section");
        }
        activityService.markSeen(familyId, parsed, userId);
    }

    private void requireMembership(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found");
        }
    }
}
