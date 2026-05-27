package org.gipsybuho.recetasfamiliares.shopping;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItemEntity, String> {

    Page<ShoppingListItemEntity> findByShoppingList_IdAndDeletedFalse(String shoppingListId, Pageable pageable);

    List<ShoppingListItemEntity> findByShoppingList_IdAndDeletedFalseOrderByPositionAsc(String shoppingListId);

    Optional<ShoppingListItemEntity> findByIdAndShoppingList_IdAndShoppingList_Family_IdAndDeletedFalse(
            String id,
            String shoppingListId,
            String familyId
    );

    Optional<ShoppingListItemEntity> findByIdAndShoppingList_Family_Id(String id, String familyId);

    List<ShoppingListItemEntity> findByShoppingList_Family_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(
            String familyId,
            Instant since
    );
}
