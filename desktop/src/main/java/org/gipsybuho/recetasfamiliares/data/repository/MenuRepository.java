package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.gipsybuho.recetasfamiliares.data.cache.SimpleCache;

import java.time.LocalDate;
import java.util.List;

public class MenuRepository {

    private final ApiClient api;
    private final AppSession session;
    private final SimpleCache<SyncDtos.MenuDtos.MenuItemDto> cache = new SimpleCache<>();

    public MenuRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public SimpleCache<SyncDtos.MenuDtos.MenuItemDto> getCache() { return cache; }

    public List<SyncDtos.MenuDtos.MenuItemDto> loadForWeek(LocalDate weekStart) throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/menu-items?weekStart=" + weekStart + "&page=0&size=100";
        SyncDtos.MenuDtos.MenuPageResponse page = api.get(path, SyncDtos.MenuDtos.MenuPageResponse.class);
        if (page.items() == null) return List.of();
        return page.items().stream().filter(i -> !i.deleted()).toList();
    }

    public SyncDtos.MenuDtos.MenuItemDto assign(String recipeId, LocalDate plannedDate, String mealType) throws ApiException {
        String familyId = session.getFamilyId();
        var req = new SyncDtos.MenuDtos.AssignMenuItemRequest(recipeId, plannedDate.toString(), mealType, null);
        return api.post("api/v1/families/" + familyId + "/menu-items", req, SyncDtos.MenuDtos.MenuItemDto.class);
    }

    public void remove(String menuItemId) throws ApiException {
        String familyId = session.getFamilyId();
        api.delete("api/v1/families/" + familyId + "/menu-items/" + menuItemId);
    }

    public void updateFromSync(List<SyncDtos.MenuDtos.MenuItemDto> items) {
        cache.mergeById(items, SyncDtos.MenuDtos.MenuItemDto::id, SyncDtos.MenuDtos.MenuItemDto::deleted);
    }
}
