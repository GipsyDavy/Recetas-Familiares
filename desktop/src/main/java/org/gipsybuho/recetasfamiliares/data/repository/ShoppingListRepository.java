package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

import java.util.List;

public class ShoppingListRepository {

    private final ApiClient api;
    private final AppSession session;

    public ShoppingListRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public List<SyncDtos.ShoppingDtos.ShoppingListDto> loadAll() throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/shopping-lists?page=0&size=50";
        SyncDtos.ShoppingDtos.ShoppingPageResponse page =
                api.get(path, SyncDtos.ShoppingDtos.ShoppingPageResponse.class);
        if (page.items() == null) return List.of();
        return page.items().stream().filter(l -> !l.deleted()).toList();
    }

    public List<SyncDtos.ShoppingDtos.ShoppingListItemDto> loadItems(String listId) throws ApiException {
        String familyId = session.getFamilyId();
        // El backend limita size a 100 (@Max); 200 provocaba un 400 silencioso.
        String path = "api/v1/families/" + familyId + "/shopping-lists/" + listId + "/items?page=0&size=100";
        SyncDtos.ShoppingDtos.ShoppingItemPageResponse page =
                api.get(path, SyncDtos.ShoppingDtos.ShoppingItemPageResponse.class);
        if (page.items() == null) return List.of();
        return page.items().stream().filter(i -> !i.deleted()).toList();
    }

    public SyncDtos.ShoppingDtos.ShoppingListItemDto checkItem(
            SyncDtos.ShoppingDtos.ShoppingListItemDto item, boolean checked) throws ApiException {
        String familyId = session.getFamilyId();
        var req = new SyncDtos.ShoppingDtos.UpdateShoppingListItemRequest(
                item.position(), item.name(), item.quantity(), item.unit(), checked, item.note());
        String path = "api/v1/families/" + familyId
                + "/shopping-lists/" + item.shoppingListId()
                + "/items/" + item.id();
        return api.put(path, req, SyncDtos.ShoppingDtos.ShoppingListItemDto.class);
    }

    public void updateFromSync(
            List<SyncDtos.ShoppingDtos.ShoppingListDto> lists,
            List<SyncDtos.ShoppingDtos.ShoppingListItemDto> items
    ) {
        // ShoppingListRepository no mantiene cache local: listas e items se cargan bajo demanda.
    }
}
