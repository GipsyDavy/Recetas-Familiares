package org.gipsybuho.recetasfamiliares.presence;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/families/{familyId}/presence")
public class PresenceController {

    private final FamilyMemberRepository familyMemberRepository;
    private final PresenceRegistry presenceRegistry;

    public PresenceController(FamilyMemberRepository familyMemberRepository, PresenceRegistry presenceRegistry) {
        this.familyMemberRepository = familyMemberRepository;
        this.presenceRegistry = presenceRegistry;
    }

    @GetMapping
    public PresenceResponse snapshot(@PathVariable String familyId, Authentication authentication) {
        String userId = authentication.getName();
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
        return new PresenceResponse(presenceRegistry.onlineUserIds(familyId));
    }
}
