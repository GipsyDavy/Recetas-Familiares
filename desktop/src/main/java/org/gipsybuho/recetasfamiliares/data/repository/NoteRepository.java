package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.gipsybuho.recetasfamiliares.data.cache.SimpleCache;

import java.util.List;

public class NoteRepository {

    private final ApiClient api;
    private final AppSession session;
    private final SimpleCache<SyncDtos.NoteDtos.FamilyNoteDto> cache = new SimpleCache<>();

    public SimpleCache<SyncDtos.NoteDtos.FamilyNoteDto> getCache() { return cache; }

    /** Must be called on the JavaFX Application Thread. */
    public void updateCache(List<SyncDtos.NoteDtos.FamilyNoteDto> notes) {
        cache.replaceAll(notes);
    }

    /** Must be called on the JavaFX Application Thread. */
    public void updateFromSync(List<SyncDtos.NoteDtos.FamilyNoteDto> notes) {
        cache.mergeById(notes, SyncDtos.NoteDtos.FamilyNoteDto::id,
                SyncDtos.NoteDtos.FamilyNoteDto::deleted);
    }

    public NoteRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public List<SyncDtos.NoteDtos.FamilyNoteDto> loadAll() throws ApiException {
        String familyId = session.getFamilyId();
        // El backend limita size a 100 (@Max); 200 provocaba un 400 silencioso.
        String path = "api/v1/families/" + familyId + "/notes?page=0&size=100";
        SyncDtos.NoteDtos.NotePageResponse page =
                api.get(path, SyncDtos.NoteDtos.NotePageResponse.class);
        if (page.items() == null) return List.of();
        return page.items().stream().filter(n -> !n.deleted()).toList();
    }

    public SyncDtos.NoteDtos.NotePageResponse loadPage(int page, int size) throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/notes?page=" + page + "&size=" + size;
        return api.get(path, SyncDtos.NoteDtos.NotePageResponse.class);
    }

    public SyncDtos.NoteDtos.FamilyNoteDto create(String title, String body, boolean pinned) throws ApiException {
        String familyId = session.getFamilyId();
        var req = new SyncDtos.NoteDtos.CreateNoteRequest(title, body, pinned);
        return api.post("api/v1/families/" + familyId + "/notes", req,
                SyncDtos.NoteDtos.FamilyNoteDto.class);
    }

    public SyncDtos.NoteDtos.FamilyNoteDto update(String noteId, String title, String body, boolean pinned) throws ApiException {
        String familyId = session.getFamilyId();
        var req = new SyncDtos.NoteDtos.UpdateNoteRequest(title, body, pinned);
        return api.put("api/v1/families/" + familyId + "/notes/" + noteId, req,
                SyncDtos.NoteDtos.FamilyNoteDto.class);
    }

    public void delete(String noteId) throws ApiException {
        String familyId = session.getFamilyId();
        api.delete("api/v1/families/" + familyId + "/notes/" + noteId);
    }
}
