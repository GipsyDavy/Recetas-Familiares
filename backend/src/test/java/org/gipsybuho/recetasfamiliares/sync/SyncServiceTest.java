package org.gipsybuho.recetasfamiliares.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeRepository;
import org.gipsybuho.recetasfamiliares.menus.MenuItemRepository;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteRepository;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeDifficulty;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepRepository;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListItemRepository;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeIngredientRepository ingredientRepository;
    @Mock
    private RecipeStepRepository stepRepository;
    @Mock
    private StockItemRepository stockItemRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private ShoppingListRepository shoppingListRepository;
    @Mock
    private ShoppingListItemRepository shoppingListItemRepository;
    @Mock
    private FavoriteRecipeRepository favoriteRecipeRepository;
    @Mock
    private FamilyNoteRepository familyNoteRepository;
    @Mock
    private RecipePhotoRepository photoRepository;
    @Mock
    private UserRepository userRepository;

    private SyncService service;

    @BeforeEach
    void setUp() {
        service = new SyncService(
                familyMemberRepository,
                familyRepository,
                recipeRepository,
                ingredientRepository,
                stepRepository,
                stockItemRepository,
                menuItemRepository,
                shoppingListRepository,
                shoppingListItemRepository,
                favoriteRecipeRepository,
                familyNoteRepository,
                photoRepository,
                userRepository
        );
    }

    @Test
    void pushNewOfflineRecipeAssignsAuthenticatedUserAsCreator() {
        FamilyEntity family = family("family-1");
        UserEntity pushUser = user("user-1", "Ana");
        String recipeId = UUID.randomUUID().toString();
        allowPush("family-1", "user-1", family, pushUser);
        when(recipeRepository.findByIdAndFamily_Id(recipeId, "family-1")).thenReturn(Optional.empty());
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SyncPullResponse response = service.push("family-1", "user-1", request(recipeId, 0L));

        assertThat(response.recipes()).hasSize(1);
        assertThat(response.recipes().get(0).createdByUserId()).isEqualTo("user-1");
        assertThat(response.recipes().get(0).createdByDisplayName()).isEqualTo("Ana");
        ArgumentCaptor<RecipeEntity> recipeCaptor = ArgumentCaptor.forClass(RecipeEntity.class);
        org.mockito.Mockito.verify(recipeRepository).save(recipeCaptor.capture());
        assertThat(recipeCaptor.getValue().getCreatedByUserId()).isEqualTo("user-1");
    }

    @Test
    void pushExistingRecipeKeepsOriginalCreator() {
        FamilyEntity family = family("family-1");
        UserEntity pushUser = user("user-1", "Ana");
        UserEntity originalCreator = user("user-2", "Luis");
        String recipeId = UUID.randomUUID().toString();
        RecipeEntity existing = new RecipeEntity(
                recipeId,
                family,
                originalCreator,
                "Original",
                null,
                2,
                5,
                10,
                RecipeDifficulty.EASY
        );
        ReflectionTestUtils.setField(existing, "syncVersion", 7L);
        allowPush("family-1", "user-1", family, pushUser);
        when(recipeRepository.findByIdAndFamily_Id(recipeId, "family-1")).thenReturn(Optional.of(existing));
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SyncPullResponse response = service.push("family-1", "user-1", request(recipeId, 7L));

        assertThat(response.recipes()).hasSize(1);
        assertThat(response.recipes().get(0).createdByUserId()).isEqualTo("user-2");
        assertThat(response.recipes().get(0).createdByDisplayName()).isEqualTo("Luis");
    }

    private void allowPush(String familyId, String userId, FamilyEntity family, UserEntity user) {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                eq(familyId),
                eq(userId),
                any()
        )).thenReturn(true);
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private static SyncPushRequest request(String recipeId, Long baseSyncVersion) {
        return new SyncPushRequest(
                List.of(new SyncRecipePushItem(
                        recipeId,
                        baseSyncVersion,
                        "Receta sincronizada",
                        null,
                        4,
                        10,
                        20,
                        RecipeDifficulty.MEDIUM,
                        false
                )),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
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
}
