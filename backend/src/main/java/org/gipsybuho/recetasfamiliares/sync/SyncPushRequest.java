package org.gipsybuho.recetasfamiliares.sync;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SyncPushRequest(
        @NotNull
        List<@NotNull @Valid SyncRecipePushItem> recipes,

        @NotNull
        List<@NotNull @Valid SyncIngredientPushItem> ingredients,

        @NotNull
        List<@NotNull @Valid SyncStepPushItem> steps,

        List<@NotNull @Valid SyncStockItemPushItem> stockItems
) {
}
