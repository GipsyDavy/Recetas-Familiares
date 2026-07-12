package org.gipsybuho.recetasfamiliares.rankings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.ratings.RecipeRatingJpaRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RecipeRankingServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeRatingJpaRepository ratingRepository;

    private RecipeRankingService service;

    @BeforeEach
    void setUp() {
        service = new RecipeRankingService(
                familyMemberRepository,
                recipeRepository,
                ratingRepository
        );
    }

    @Test
    void blocksRankingForUsersOutsideFamily() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse("family-1", "outsider"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.userRecipeRanking("family-1", "outsider"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(familyMemberRepository, never()).findMembersWithUserByFamilyId("family-1");
        verify(recipeRepository, never()).countActiveRecipesByCreator("family-1");
        verify(ratingRepository, never()).aggregateReceivedRatingsByRecipeCreator("family-1");
    }

    @Test
    void ranksActiveFamilyMembersByRecipesAndReceivedStars() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse("family-1", "viewer"))
                .thenReturn(true);
        when(familyMemberRepository.findMembersWithUserByFamilyId("family-1"))
                .thenReturn(List.of(
                        member("user-a", "Ana", FamilyRole.ADMIN),
                        member("user-b", "Bruno", FamilyRole.MEMBER),
                        member("user-c", "Carla", FamilyRole.MEMBER)
                ));
        when(recipeRepository.countActiveRecipesByCreator("family-1"))
                .thenReturn(List.of(
                        recipeCount("user-a", 2),
                        recipeCount("user-b", 3)
                ));
        when(ratingRepository.aggregateReceivedRatingsByRecipeCreator("family-1"))
                .thenReturn(List.of(
                        ratingStats("user-a", 2, 4.5, 9),
                        ratingStats("user-b", 1, 5.0, 5)
                ));

        List<UserRecipeRankingResponse> ranking = service.userRecipeRanking("family-1", "viewer");

        assertThat(ranking).extracting(UserRecipeRankingResponse::userId)
                .containsExactly("user-a", "user-b", "user-c");
        assertThat(ranking).extracting(UserRecipeRankingResponse::rank)
                .containsExactly(1, 2, 3);
        assertThat(ranking.get(0).displayName()).isEqualTo("Ana");
        assertThat(ranking.get(0).role()).isEqualTo("ADMIN");
        assertThat(ranking.get(0).recipesCreated()).isEqualTo(2);
        assertThat(ranking.get(0).ratingsReceived()).isEqualTo(2);
        assertThat(ranking.get(0).averageStars()).isEqualTo(4.5);
        assertThat(ranking.get(0).score()).isEqualTo(11);
        assertThat(ranking.get(2).recipesCreated()).isZero();
        assertThat(ranking.get(2).ratingsReceived()).isZero();
        assertThat(ranking.get(2).averageStars()).isNull();
        assertThat(ranking.get(2).score()).isZero();
    }

    @Test
    void usesStableTieBreakers() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse("family-1", "viewer"))
                .thenReturn(true);
        when(familyMemberRepository.findMembersWithUserByFamilyId("family-1"))
                .thenReturn(List.of(
                        member("user-b", "Bruno", FamilyRole.MEMBER),
                        member("user-a", "Ana", FamilyRole.MEMBER)
                ));
        when(recipeRepository.countActiveRecipesByCreator("family-1"))
                .thenReturn(List.of(
                        recipeCount("user-b", 1),
                        recipeCount("user-a", 1)
                ));
        when(ratingRepository.aggregateReceivedRatingsByRecipeCreator("family-1"))
                .thenReturn(List.of(
                        ratingStats("user-b", 1, 4.0, 4),
                        ratingStats("user-a", 1, 4.0, 4)
                ));

        List<UserRecipeRankingResponse> ranking = service.userRecipeRanking("family-1", "viewer");

        assertThat(ranking).extracting(UserRecipeRankingResponse::displayName)
                .containsExactly("Ana", "Bruno");
        assertThat(ranking).extracting(UserRecipeRankingResponse::rank)
                .containsExactly(1, 2);
    }

    private static FamilyMemberEntity member(String userId, String displayName, FamilyRole role) {
        return new FamilyMemberEntity(family("family-1"), user(userId, displayName), role);
    }

    private static FamilyEntity family(String id) {
        FamilyEntity family = new FamilyEntity("Familia");
        ReflectionTestUtils.setField(family, "id", id);
        return family;
    }

    private static UserEntity user(String id, String displayName) {
        UserEntity user = new UserEntity(id + "@example.com", displayName, "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static RecipeRepository.CreatorRecipeCount recipeCount(String userId, long count) {
        return new RecipeRepository.CreatorRecipeCount() {
            @Override
            public String getUserId() {
                return userId;
            }

            @Override
            public long getRecipeCount() {
                return count;
            }
        };
    }

    private static RecipeRatingJpaRepository.CreatorRatingStats ratingStats(
            String userId,
            long ratingsReceived,
            Double averageStars,
            long starsReceived
    ) {
        return new RecipeRatingJpaRepository.CreatorRatingStats() {
            @Override
            public String getUserId() {
                return userId;
            }

            @Override
            public long getRatingsReceived() {
                return ratingsReceived;
            }

            @Override
            public Double getAverageStars() {
                return averageStars;
            }

            @Override
            public Long getStarsReceived() {
                return starsReceived;
            }
        };
    }
}
