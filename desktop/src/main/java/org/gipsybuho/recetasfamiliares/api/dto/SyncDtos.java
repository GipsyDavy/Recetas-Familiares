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
            List<PhotoDtos.RecipePhotoDto> recipePhotos,
            boolean hasMore,
            String nextSince
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

        public record ShoppingListDto(
                String id, String familyId, String name,
                String plannedFrom, String plannedTo, String note,
                boolean completed, String createdAt, String updatedAt,
                long syncVersion, boolean deleted
        ) {}

        public record ShoppingListItemDto(
                String id, String shoppingListId, int position, String name,
                Double quantity, String unit, boolean checked, String note,
                String createdAt, String updatedAt, long syncVersion, boolean deleted
        ) {}

        public record ShoppingPageResponse(
                java.util.List<ShoppingListDto> content,
                int totalElements, int totalPages, int number
        ) {}

        public record ShoppingItemPageResponse(
                java.util.List<ShoppingListItemDto> content,
                int totalElements, int totalPages, int number
        ) {}

        public record UpdateShoppingListItemRequest(
                int position, String name, Double quantity,
                String unit, boolean checked, String note
        ) {}
    }

    public static final class FavoriteDtos {
        private FavoriteDtos() {}

        public record FavoriteRecipeDto(
                String id, String familyId, String recipeId, String recipeTitle,
                String createdAt, String updatedAt, long syncVersion, boolean deleted
        ) {}

        public record AddFavoriteRequest(String recipeId) {}

        public record FavoritePageResponse(
                java.util.List<FavoriteRecipeDto> content,
                int totalElements, int totalPages, int number
        ) {}
    }

    public static final class NoteDtos {
        private NoteDtos() {}

        public record FamilyNoteDto(
                String id, String familyId, String recipeId, String recipeTitle,
                String title, String body, boolean pinned, String createdAt,
                String updatedAt, long syncVersion, boolean deleted
        ) {}

        public record NotePageResponse(
                java.util.List<FamilyNoteDto> content,
                int totalElements, int totalPages, int number
        ) {}

        public record CreateNoteRequest(String title, String body, boolean pinned) {}

        public record UpdateNoteRequest(String title, String body, boolean pinned) {}
    }

    public static final class PhotoDtos {
        private PhotoDtos() {}
        public record RecipePhotoDto(String id, String recipeId, String url) {}
    }
}
