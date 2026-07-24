package org.gipsybuho.recetasfamiliares.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class FamilyActivityServiceTest {

    private static final String FAMILY_ID = "fam-1";
    private static final String ACTOR_ID = "user-actor";
    private static final String OTHER_USER_ID = "user-other";

    private FamilySectionActivityRepository activityRepository;
    private UserSectionLastSeenRepository lastSeenRepository;
    private FamilyActivityService service;

    @BeforeEach
    void setUp() {
        activityRepository = Mockito.mock(FamilySectionActivityRepository.class);
        lastSeenRepository = Mockito.mock(UserSectionLastSeenRepository.class);
        service = new FamilyActivityService(activityRepository, lastSeenRepository);
    }

    @Test
    void recordActivityCreatesRowWhenNoneExistsAndMarksActorAsSeen() {
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.empty());
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(ACTOR_ID, FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.empty());

        service.recordActivity(FAMILY_ID, FamilySection.RECIPE, ACTOR_ID);

        ArgumentCaptor<FamilySectionActivityEntity> activityCaptor =
                ArgumentCaptor.forClass(FamilySectionActivityEntity.class);
        verify(activityRepository).save(activityCaptor.capture());
        assertEquals(FAMILY_ID, activityCaptor.getValue().getFamilyId());
        assertEquals(FamilySection.RECIPE, activityCaptor.getValue().getSection());

        ArgumentCaptor<UserSectionLastSeenEntity> seenCaptor =
                ArgumentCaptor.forClass(UserSectionLastSeenEntity.class);
        verify(lastSeenRepository).save(seenCaptor.capture());
        assertEquals(ACTOR_ID, seenCaptor.getValue().getUserId());
    }

    @Test
    void recordActivityUpdatesExistingRowInPlace() {
        FamilySectionActivityEntity existing =
                new FamilySectionActivityEntity(FAMILY_ID, FamilySection.NOTE, Instant.EPOCH);
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.NOTE))
                .thenReturn(Optional.of(existing));
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(ACTOR_ID, FAMILY_ID, FamilySection.NOTE))
                .thenReturn(Optional.empty());

        service.recordActivity(FAMILY_ID, FamilySection.NOTE, ACTOR_ID);

        assertTrue(existing.getLastActivityAt().isAfter(Instant.EPOCH));
        verify(activityRepository).save(existing);
    }

    @Test
    void markSeenUpdatesExistingRowInPlace() {
        UserSectionLastSeenEntity existing =
                new UserSectionLastSeenEntity(ACTOR_ID, FAMILY_ID, FamilySection.STOCK, Instant.EPOCH);
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(ACTOR_ID, FAMILY_ID, FamilySection.STOCK))
                .thenReturn(Optional.of(existing));

        service.markSeen(FAMILY_ID, FamilySection.STOCK, ACTOR_ID);

        assertTrue(existing.getLastSeenAt().isAfter(Instant.EPOCH));
        verify(lastSeenRepository).save(existing);
    }

    @Test
    void unseenSectionsReturnsTrueWhenNeverSeenButActivityExists() {
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.of(new FamilySectionActivityEntity(FAMILY_ID, FamilySection.RECIPE, Instant.now())));
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.NOTE))
                .thenReturn(Optional.empty());
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.STOCK))
                .thenReturn(Optional.empty());
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(OTHER_USER_ID, FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.empty());

        Map<FamilySection, Boolean> result = service.unseenSections(FAMILY_ID, OTHER_USER_ID);

        assertTrue(result.get(FamilySection.RECIPE));
        assertFalse(result.get(FamilySection.NOTE));
        assertFalse(result.get(FamilySection.STOCK));
    }

    @Test
    void unseenSectionsReturnsFalseWhenLastSeenIsAfterLastActivity() {
        Instant activityTime = Instant.now().minusSeconds(60);
        Instant seenTime = Instant.now();
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.of(new FamilySectionActivityEntity(FAMILY_ID, FamilySection.RECIPE, activityTime)));
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.NOTE)).thenReturn(Optional.empty());
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.STOCK)).thenReturn(Optional.empty());
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(OTHER_USER_ID, FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.of(new UserSectionLastSeenEntity(OTHER_USER_ID, FAMILY_ID, FamilySection.RECIPE, seenTime)));

        Map<FamilySection, Boolean> result = service.unseenSections(FAMILY_ID, OTHER_USER_ID);

        assertFalse(result.get(FamilySection.RECIPE));
    }
}
