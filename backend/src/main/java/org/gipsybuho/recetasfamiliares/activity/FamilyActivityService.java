package org.gipsybuho.recetasfamiliares.activity;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unico punto de escritura de actividad de familia (avisos de recetas/notas/
 * stock, item 20 del roadmap). "No visto" se calcula comparando timestamps,
 * sin contar eventos individuales -- mismo criterio que el fix aplicado hoy
 * en PrivateInboxPing (contar eventos permite sobre-contar en ediciones).
 */
@Service
public class FamilyActivityService {

    private final FamilySectionActivityRepository activityRepository;
    private final UserSectionLastSeenRepository lastSeenRepository;

    public FamilyActivityService(
            FamilySectionActivityRepository activityRepository,
            UserSectionLastSeenRepository lastSeenRepository
    ) {
        this.activityRepository = activityRepository;
        this.lastSeenRepository = lastSeenRepository;
    }

    @Transactional
    public void recordActivity(String familyId, FamilySection section, String actorUserId) {
        Instant now = Instant.now();
        FamilySectionActivityEntity activity = activityRepository
                .findByFamilyIdAndSection(familyId, section)
                .orElseGet(() -> new FamilySectionActivityEntity(familyId, section, now));
        activity.touch(now);
        activityRepository.save(activity);
        markSeenAt(familyId, section, actorUserId, now);
    }

    @Transactional
    public void markSeen(String familyId, FamilySection section, String userId) {
        markSeenAt(familyId, section, userId, Instant.now());
    }

    private void markSeenAt(String familyId, FamilySection section, String userId, Instant when) {
        UserSectionLastSeenEntity seen = lastSeenRepository
                .findByUserIdAndFamilyIdAndSection(userId, familyId, section)
                .orElseGet(() -> new UserSectionLastSeenEntity(userId, familyId, section, when));
        seen.touch(when);
        lastSeenRepository.save(seen);
    }

    @Transactional(readOnly = true)
    public Map<FamilySection, Boolean> unseenSections(String familyId, String userId) {
        Map<FamilySection, Boolean> result = new EnumMap<>(FamilySection.class);
        for (FamilySection section : FamilySection.values()) {
            Instant lastActivity = activityRepository.findByFamilyIdAndSection(familyId, section)
                    .map(FamilySectionActivityEntity::getLastActivityAt)
                    .orElse(null);
            if (lastActivity == null) {
                result.put(section, false);
                continue;
            }
            Instant lastSeen = lastSeenRepository.findByUserIdAndFamilyIdAndSection(userId, familyId, section)
                    .map(UserSectionLastSeenEntity::getLastSeenAt)
                    .orElse(Instant.EPOCH);
            result.put(section, lastActivity.isAfter(lastSeen));
        }
        return result;
    }
}
