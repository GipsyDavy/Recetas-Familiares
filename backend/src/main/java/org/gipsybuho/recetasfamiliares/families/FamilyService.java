package org.gipsybuho.recetasfamiliares.families;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyService {

    private final FamilyMemberRepository familyMemberRepository;

    public FamilyService(FamilyMemberRepository familyMemberRepository) {
        this.familyMemberRepository = familyMemberRepository;
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
}
