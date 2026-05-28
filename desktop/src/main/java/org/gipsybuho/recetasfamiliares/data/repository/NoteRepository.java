package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

import java.util.List;

public class NoteRepository {

    private final ApiClient api;
    private final AppSession session;

    public NoteRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public List<SyncDtos.NoteDtos.FamilyNoteDto> loadAll() throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/family-notes?page=0&size=200";
        SyncDtos.NoteDtos.NotePageResponse page =
                api.get(path, SyncDtos.NoteDtos.NotePageResponse.class);
        if (page.content() == null) return List.of();
        return page.content().stream().filter(n -> !n.deleted()).toList();
    }

    public SyncDtos.NoteDtos.FamilyNoteDto create(String title, String body, boolean pinned) throws ApiException {
        String familyId = session.getFamilyId();
        var req = new SyncDtos.NoteDtos.CreateNoteRequest(title, body, pinned);
        return api.post("api/v1/families/" + familyId + "/family-notes", req,
                SyncDtos.NoteDtos.FamilyNoteDto.class);
    }

    public SyncDtos.NoteDtos.FamilyNoteDto update(String noteId, String title, String body, boolean pinned) throws ApiException {
        String familyId = session.getFamilyId();
        var req = new SyncDtos.NoteDtos.UpdateNoteRequest(title, body, pinned);
        return api.put("api/v1/families/" + familyId + "/family-notes/" + noteId, req,
                SyncDtos.NoteDtos.FamilyNoteDto.class);
    }

    public void delete(String noteId) throws ApiException {
        String familyId = session.getFamilyId();
        api.delete("api/v1/families/" + familyId + "/family-notes/" + noteId);
    }
}
