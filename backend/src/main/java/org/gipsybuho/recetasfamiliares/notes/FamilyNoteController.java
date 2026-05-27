package org.gipsybuho.recetasfamiliares.notes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/families/{familyId}/notes")
public class FamilyNoteController {

    private final FamilyNoteService noteService;

    public FamilyNoteController(FamilyNoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public PageResponse<FamilyNoteResponse> listNotes(
            @PathVariable String familyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return noteService.listNotes(familyId, authentication.getName(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyNoteResponse createNote(
            @PathVariable String familyId,
            @Valid @RequestBody CreateFamilyNoteRequest request,
            Authentication authentication
    ) {
        return noteService.createNote(familyId, authentication.getName(), request);
    }

    @GetMapping("/{noteId}")
    public FamilyNoteResponse getNote(
            @PathVariable String familyId,
            @PathVariable String noteId,
            Authentication authentication
    ) {
        return noteService.getNote(familyId, noteId, authentication.getName());
    }

    @PutMapping("/{noteId}")
    public FamilyNoteResponse updateNote(
            @PathVariable String familyId,
            @PathVariable String noteId,
            @Valid @RequestBody UpdateFamilyNoteRequest request,
            Authentication authentication
    ) {
        return noteService.updateNote(familyId, noteId, authentication.getName(), request);
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(
            @PathVariable String familyId,
            @PathVariable String noteId,
            Authentication authentication
    ) {
        noteService.deleteNote(familyId, noteId, authentication.getName());
    }
}
