package org.gipsybuho.recetasfamiliares.rankings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.ratings.RecipeRatingJpaRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecipeRankingService {

    private final FamilyMemberRepository familyMemberRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeRatingJpaRepository ratingRepository;

    public RecipeRankingService(
            FamilyMemberRepository familyMemberRepository,
            RecipeRepository recipeRepository,
            RecipeRatingJpaRepository ratingRepository
    ) {
        this.familyMemberRepository = familyMemberRepository;
        this.recipeRepository = recipeRepository;
        this.ratingRepository = ratingRepository;
    }

    @Transactional(readOnly = true)
    public List<UserRecipeRankingResponse> userRecipeRanking(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }

        List<FamilyMemberEntity> members = familyMemberRepository.findMembersWithUserByFamilyId(familyId);
        Map<String, RecipeRepository.CreatorRecipeCount> recipeCounts = recipeRepository
                .countActiveRecipesByCreator(familyId)
                .stream()
                .collect(Collectors.toMap(RecipeRepository.CreatorRecipeCount::getUserId, Function.identity()));
        Map<String, RecipeRatingJpaRepository.CreatorRatingStats> ratingStats = ratingRepository
                .aggregateReceivedRatingsByRecipeCreator(familyId)
                .stream()
                .collect(Collectors.toMap(RecipeRatingJpaRepository.CreatorRatingStats::getUserId, Function.identity()));

        List<UserRecipeRankingResponse> sorted = members.stream()
                .map(member -> toUnrankedResponse(member, recipeCounts, ratingStats))
                .sorted(Comparator
                        .comparingLong(UserRecipeRankingResponse::score).reversed()
                        .thenComparing(Comparator.comparing(
                                UserRecipeRankingResponse::averageStars,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                        .thenComparing(Comparator.comparingLong(
                                UserRecipeRankingResponse::ratingsReceived
                        ).reversed())
                        .thenComparing(Comparator.comparingLong(
                                UserRecipeRankingResponse::recipesCreated
                        ).reversed())
                        .thenComparing(UserRecipeRankingResponse::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<UserRecipeRankingResponse> ranked = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            UserRecipeRankingResponse item = sorted.get(i);
            ranked.add(new UserRecipeRankingResponse(
                    i + 1,
                    item.userId(),
                    item.displayName(),
                    item.role(),
                    item.recipesCreated(),
                    item.ratingsReceived(),
                    item.averageStars(),
                    item.score()
            ));
        }
        return ranked;
    }

    private UserRecipeRankingResponse toUnrankedResponse(
            FamilyMemberEntity member,
            Map<String, RecipeRepository.CreatorRecipeCount> recipeCounts,
            Map<String, RecipeRatingJpaRepository.CreatorRatingStats> ratingStats
    ) {
        String memberUserId = member.getUser().getId();
        RecipeRepository.CreatorRecipeCount recipeCount = recipeCounts.get(memberUserId);
        RecipeRatingJpaRepository.CreatorRatingStats rating = ratingStats.get(memberUserId);
        long recipesCreated = recipeCount == null ? 0L : recipeCount.getRecipeCount();
        long ratingsReceived = rating == null ? 0L : rating.getRatingsReceived();
        long starsReceived = rating == null || rating.getStarsReceived() == null ? 0L : rating.getStarsReceived();
        return new UserRecipeRankingResponse(
                0,
                memberUserId,
                member.getUser().getDisplayName(),
                member.getRole().name(),
                recipesCreated,
                ratingsReceived,
                rating == null ? null : rating.getAverageStars(),
                recipesCreated + starsReceived
        );
    }
}
