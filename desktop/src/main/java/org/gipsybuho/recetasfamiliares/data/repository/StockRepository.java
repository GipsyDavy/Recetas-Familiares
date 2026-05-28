package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.gipsybuho.recetasfamiliares.data.cache.SimpleCache;

import java.util.List;

public class StockRepository {

    private final ApiClient api;
    private final AppSession session;
    private final SimpleCache<StockDtos.StockItemDto> cache = new SimpleCache<>();

    public StockRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public SimpleCache<StockDtos.StockItemDto> getCache() { return cache; }

    public List<StockDtos.StockItemDto> load() throws ApiException {
        String familyId = session.getFamilyId();
        StockDtos.StockItemDto[] result = api.get(
                "api/v1/families/" + familyId + "/stock-items", StockDtos.StockItemDto[].class);
        return List.of(result);
    }

    public void updateFromSync(List<StockDtos.StockItemDto> items) {
        if (items != null) cache.replaceAll(items.stream().filter(i -> !i.deleted()).toList());
    }
}
