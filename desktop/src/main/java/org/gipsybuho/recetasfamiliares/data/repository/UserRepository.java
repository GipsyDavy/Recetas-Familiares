package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.UserDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

public class UserRepository {

    private final ApiClient api;
    private final AppSession session;

    public UserRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public UserDtos.UserResponse updateDisplayName(String newName) throws ApiException {
        UserDtos.UserResponse response = api.put("api/v1/users/me",
                new UserDtos.UpdateDisplayNameRequest(newName.trim()),
                UserDtos.UserResponse.class);
        session.setUserInfo(response.displayName(), response.email());
        return response;
    }
}
