package org.gipsybuho.recetasfamiliares.notes;

import java.util.List;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FamilyNoteService {

    private final FamilyNoteRepository noteRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final RecipeRepository recipeRepository;

    public FamilyNoteService(
            FamilyNoteRepository noteRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            RecipeRepository recipeRepository
    ) {
        this.noteRepository = noteRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.recipeRepository = recipeRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<FamilyNoteResponse> listNotes(String familyId, String userId, int page, int size) {
        requireMembership(familyId, userId);
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "pinned").and(Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        Page<FamilyNoteEntity> notes = noteRepository.findByFamily_IdAndDeletedFalse(familyId, pageable);
        return new PageResponse<>(
                notes.getContent().stream().map(this::toResponse).toList(),
                notes.getNumber(),
                notes.getSize(),
                notes.getTotalElements(),
                notes.getTotalPages()
        );
    }

    @Transactional
    public FamilyNoteResponse createNote(String familyId, String userId, CreateFamilyNoteRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        RecipeEntity recipe = resolveActiveRecipe(familyId, request.recipeId());
        FamilyNoteEntity note = new FamilyNoteEntity(
                family,
                recipe,
                request.title().trim(),
                request.body().trim(),
                request.pinned()
        );
        return toResponse(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public FamilyNoteResponse getNote(String familyId, String noteId, String userId) {
        requireMembership(familyId, userId);
        return toResponse(requireActiveNote(familyId, noteId));
    }

    @Transactional
    public FamilyNoteResponse updateNote(String familyId, String noteId, String userId, UpdateFamilyNoteRequest request) {
        requireEditor(familyId, userId);
        FamilyNoteEntity note = requireActiveNote(familyId, noteId);
        RecipeEntity recipe = resolveActiveRecipe(familyId, request.recipeId());
        note.update(recipe, request.title().trim(), request.body().trim(), request.pinned());
        return toResponse(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(String familyId, String noteId, String userId) {
        requireEditor(familyId, userId);
        FamilyNoteEntity note = requireActiveNote(familyId, noteId);
        note.softDelete();
        noteRepository.save(note);
    }

    private void requireMembership(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
    }

    private void requireEditor(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                familyId,
                userId,
                List.of(FamilyRole.OWNER, FamilyRole.ADMIN)
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family write access denied");
        }
    }

    private FamilyNoteEntity requireActiveNote(String familyId, String noteId) {
        return noteRepository.findByIdAndFamily_IdAndDeletedFalse(noteId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family note not found"));
    }

    private RecipeEntity resolveActiveRecipe(String familyId, String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe not found for note"));
    }

    private FamilyNoteResponse toResponse(FamilyNoteEntity note) {
        return new FamilyNoteResponse(
                note.getId(),
                note.getFamilyId(),
                note.getRecipeId(),
                note.getRecipeTitle(),
                note.getTitle(),
                note.getBody(),
                note.isPinned(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                note.getSyncVersion(),
                note.isDeleted()
        );
    }
}
