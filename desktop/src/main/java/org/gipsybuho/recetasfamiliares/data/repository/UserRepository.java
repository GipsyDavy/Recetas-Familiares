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

    /**
     * Consulta el perfil propio y refresca la sesion. Sirve para rellenar el
     * userId en sesiones persistidas antes de que se guardara (chat familiar).
     */
    public UserDtos.UserResponse fetchMe() throws ApiException {
        UserDtos.UserResponse response = api.get("api/v1/users/me", UserDtos.UserResponse.class);
        session.setUserInfo(response.displayName(), response.email());
        session.setAvatarUrl(response.avatarUrl());
        session.setUserId(response.id());
        return response;
    }

    public UserDtos.UserResponse updateDisplayName(String newName) throws ApiException {
        UserDtos.UserResponse response = api.put("api/v1/users/me",
                new UserDtos.UpdateDisplayNameRequest(newName.trim()),
                UserDtos.UserResponse.class);
        session.setUserInfo(response.displayName(), response.email());
        session.setAvatarUrl(response.avatarUrl());
        return response;
    }

    public UserDtos.UserResponse uploadAvatar(java.io.File file) throws ApiException {
        UserDtos.UserResponse response = api.postMultipart(
                "api/v1/users/me/avatar", file, "file", UserDtos.UserResponse.class);
        session.setUserInfo(response.displayName(), response.email());
        session.setAvatarUrl(response.avatarUrl());
        return response;
    }
}
