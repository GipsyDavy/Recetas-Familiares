package org.gipsybuho.recetasfamiliares.api.dto;

import java.util.List;

public final class SyncDtos {

    private SyncDtos() {}

    public record SyncPullResponse(
            String serverTime,
            List<RecipeDtos.RecipeDto> recipes,
            List<RecipeDtos.RecipeIngredientDto> ingredients,
            List<RecipeDtos.RecipeStepDto> steps,
            List<StockDtos.StockItemDto> stockItems,
            List<MenuDtos.MenuItemDto> menuItems,
            List<ShoppingDtos.ShoppingListDto> shoppingLists,
            List<ShoppingDtos.ShoppingListItemDto> shoppingListItems,
            List<FavoriteDtos.FavoriteRecipeDto> favoriteRecipes,
            List<NoteDtos.FamilyNoteDto> familyNotes,
            List<PhotoDtos.RecipePhotoDto> recipePhotos
    ) {}

    public record SyncPushRequest(
            List<SyncRecipePush> recipes,
            List<SyncIngredientPush> ingredients,
            List<SyncStepPush> steps
    ) {}

    public record SyncRecipePush(
            String id,
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            String difficulty,
            long syncVersion,
            boolean deleted
    ) {}

    public record SyncIngredientPush(
            String id,
            String recipeId,
            String name,
            String quantity,
            String unit,
            Integer sortOrder,
            long syncVersion,
            boolean deleted
    ) {}

    public record SyncStepPush(
            String id,
            String recipeId,
            Integer stepNumber,
            String description,
            Integer durationMinutes,
            long syncVersion,
            boolean deleted
    ) {}

    public static final class MenuDtos {
        private MenuDtos() {}

        public record MenuItemDto(
                String id,
                String familyId,
                String recipeId,
                String recipeTitle,
                String plannedDate,
                String mealType,
                String note,
                String createdAt,
                String updatedAt,
                long syncVersion,
                boolean deleted
        ) {}

        public record MenuPageResponse(
                java.util.List<MenuItemDto> content,
                int totalElements,
                int totalPages,
                int number
        ) {}

        public record AssignMenuItemRequest(
                String recipeId,
                String plannedDate,
                String mealType,
                String note
        ) {}
    }

    public static final class ShoppingDtos {
        private ShoppingDtos() {}
        public record ShoppingListDto(String id) {}
        public record ShoppingListItemDto(String id) {}
    }

    public static final class FavoriteDtos {
        private FavoriteDtos() {}
        public record FavoriteRecipeDto(String id, String recipeId) {}
    }

    public static final class NoteDtos {
        private NoteDtos() {}
        public record FamilyNoteDto(String id, String content) {}
    }

    public static final class PhotoDtos {
        private PhotoDtos() {}
        public record RecipePhotoDto(String id, String recipeId, String url) {}
    }
}
