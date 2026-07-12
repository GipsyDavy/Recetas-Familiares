package org.gipsybuho.recetasfamiliares.recipes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoEntity;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeIngredientRepository ingredientRepository;
    @Mock
    private RecipeStepRepository stepRepository;
    @Mock
    private RecipePhotoRepository photoRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private UserRepository userRepository;

    private RecipeService service;

    @BeforeEach
    void setUp() {
        service = new RecipeService(
                recipeRepository,
                ingredientRepository,
                stepRepository,
                photoRepository,
                familyRepository,
                familyMemberRepository,
                userRepository
        );
    }

    @Test
    void createRecipeAssignsAuthenticatedUserAsCreator() {
        FamilyEntity family = family("family-1");
        UserEntity creator = user("user-1", "Ana");
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                eq("family-1"),
                eq("user-1"),
                any()
        )).thenReturn(true);
        when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(creator));
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecipeResponse response = service.createRecipe(
                "family-1",
                "user-1",
                new CreateRecipeRequest(" Tortilla ", null, 4, 10, 20, RecipeDifficulty.EASY)
        );

        assertThat(response.createdByUserId()).isEqualTo("user-1");
        assertThat(response.createdByDisplayName()).isEqualTo("Ana");
        ArgumentCaptor<RecipeEntity> recipeCaptor = ArgumentCaptor.forClass(RecipeEntity.class);
        verify(recipeRepository).save(recipeCaptor.capture());
        assertThat(recipeCaptor.getValue().getCreatedByUserId()).isEqualTo("user-1");
    }

    @Test
    void copyRecipeCopiesContentIntoTargetFamily() {
        FamilyEntity sourceFamily = family("source-family");
        FamilyEntity targetFamily = family("target-family");
        RecipeEntity source = recipe("recipe-1", sourceFamily);
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(
                "source-family",
                "user-1"
        )).thenReturn(true);
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                eq("target-family"),
                eq("user-1"),
                any()
        )).thenReturn(true);
        when(recipeRepository.findByIdAndFamily_IdAndDeletedFalse("recipe-1", "source-family"))
                .thenReturn(Optional.of(source));
        when(familyRepository.findById("target-family")).thenReturn(Optional.of(targetFamily));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user("user-1", "Ana")));
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> {
            RecipeEntity copy = invocation.getArgument(0);
            ReflectionTestUtils.setField(copy, "id", "copy-1");
            return copy;
        });
        when(ingredientRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc("recipe-1"))
                .thenReturn(List.of(new RecipeIngredientEntity(
                        source,
                        1,
                        "Tomate",
                        BigDecimal.valueOf(2),
                        "ud",
                        "maduro"
                )));
        when(stepRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc("recipe-1"))
                .thenReturn(List.of(new RecipeStepEntity(source, 1, "Cortar", 5)));
        when(photoRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc("recipe-1"))
                .thenReturn(List.of(new RecipePhotoEntity(
                        source,
                        1,
                        "/uploads/photo.jpg",
                        "/uploads/photo.jpg",
                        "Plato",
                        "image/jpeg",
                        123L,
                        "/uploads/photo.jpg"
                )));

        RecipeResponse response = service.copyRecipe(
                "source-family",
                "recipe-1",
                "user-1",
                new CopyRecipeRequest(" target-family ")
        );

        assertThat(response.id()).isEqualTo("copy-1");
        assertThat(response.familyId()).isEqualTo("target-family");
        assertThat(response.title()).isEqualTo("Receta original");
        assertThat(response.createdByUserId()).isEqualTo("user-1");
        assertThat(response.createdByDisplayName()).isEqualTo("Ana");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecipeIngredientEntity>> ingredientCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecipeStepEntity>> stepCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecipePhotoEntity>> photoCaptor = ArgumentCaptor.forClass(List.class);
        verify(ingredientRepository).saveAll(ingredientCaptor.capture());
        verify(stepRepository).saveAll(stepCaptor.capture());
        verify(photoRepository).saveAll(photoCaptor.capture());

        assertThat(ingredientCaptor.getValue().get(0).getRecipeId()).isEqualTo("copy-1");
        assertThat(ingredientCaptor.getValue().get(0).getName()).isEqualTo("Tomate");
        assertThat(stepCaptor.getValue().get(0).getRecipeId()).isEqualTo("copy-1");
        assertThat(stepCaptor.getValue().get(0).getInstruction()).isEqualTo("Cortar");
        assertThat(photoCaptor.getValue().get(0).getRecipeId()).isEqualTo("copy-1");
        assertThat(photoCaptor.getValue().get(0).getStoragePath()).isEqualTo("/uploads/photo.jpg");
    }

    @Test
    void copyRecipeRequiresSourceMembership() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(
                "source-family",
                "user-1"
        )).thenReturn(false);

        assertThatThrownBy(() -> service.copyRecipe(
                "source-family",
                "recipe-1",
                "user-1",
                new CopyRecipeRequest("target-family")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(recipeRepository, never()).findByIdAndFamily_IdAndDeletedFalse(any(), any());
        verify(ingredientRepository, never()).saveAll(anyIterable());
    }

    @Test
    void copyRecipeRequiresAdminRoleInTargetFamily() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(
                "source-family",
                "user-1"
        )).thenReturn(true);
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                eq("target-family"),
                eq("user-1"),
                any()
        )).thenReturn(false);

        assertThatThrownBy(() -> service.copyRecipe(
                "source-family",
                "recipe-1",
                "user-1",
                new CopyRecipeRequest("target-family")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(recipeRepository, never()).findByIdAndFamily_IdAndDeletedFalse(any(), any());
        verify(photoRepository, never()).saveAll(anyIterable());
    }

    @Test
    void copyRecipeRejectsSameFamilyAsTarget() {
        assertThatThrownBy(() -> service.copyRecipe(
                "source-family",
                "recipe-1",
                "user-1",
                new CopyRecipeRequest("source-family")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(recipeRepository, never()).save(any(RecipeEntity.class));
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

    private static RecipeEntity recipe(String id, FamilyEntity family) {
        return new RecipeEntity(
                id,
                family,
                "Receta original",
                "Descripcion",
                4,
                10,
                20,
                RecipeDifficulty.EASY
        );
    }
}
