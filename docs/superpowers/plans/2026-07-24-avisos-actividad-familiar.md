# Avisos de Actividad Familiar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un miembro de la familia ve un indicador simple (sin numero) en Recetas, Stock y Notas cuando otro miembro crea, edita o borra algo en esa seccion desde la ultima vez que la visito — persistente entre sesiones, solo mientras la app esta abierta (sin push real).

**Architecture:** Dos tablas backend (`family_section_activity`, `user_section_last_seen`) comparan timestamps en vez de contar eventos — estructuralmente inmune al bug de sobre-conteo que se corrigio hoy mismo en el ping de inbox del chat privado. Un servicio unico (`FamilyActivityService`) instrumenta los 3 servicios de dominio existentes. Tiempo real reutiliza el socket de presencia ya conectado (nuevo topic `/topic/families/{familyId}/activity`), sin conexion nueva. Los clientes (Android/Desktop) muestran un badge sin numero sobre cada tab/item ya existente.

**Tech Stack:** Spring Boot + PostgreSQL/Flyway (backend), Kotlin + Jetpack Compose (Android), JavaFX (Desktop).

---

## File Manifest

| File | Change |
|---|---|
| `backend/src/main/resources/db/migration/V20__create_family_activity_schema.sql` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySection.java` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySectionActivityEntity.java` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySectionActivityRepository.java` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/UserSectionLastSeenEntity.java` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/UserSectionLastSeenRepository.java` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityService.java` | Create |
| `backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityServiceTest.java` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/recipes/RecipeService.java` | Modify |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/notes/FamilyNoteService.java` | Modify |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/stock/StockItemService.java` | Modify |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityResponse.java` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityController.java` | Create |
| `backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityControllerTest.java` | Create |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptor.java` | Modify |
| `backend/src/test/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptorTest.java` | Modify |
| `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityRealtimePublisher.java` | Create |
| `backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityRealtimePublisherTest.java` | Create |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt` | Modify |
| `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt` | Modify |
| `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/FamilyDtos.java` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepository.java` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java` | Modify |

---

## BACKEND

### Task 1: Migracion Flyway + entidades JPA

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__create_family_activity_schema.sql`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySection.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySectionActivityEntity.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySectionActivityRepository.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/UserSectionLastSeenEntity.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/UserSectionLastSeenRepository.java`

Ultima migracion real (verificado con `sort -V`, no alfabetico) es `V19__create_private_chat_schema.sql` — esta es `V20`. (Nota: la version original de este plan decia V10 por un error de ordenamiento alfabetico vs numerico al listar migraciones; corregido tras ejecutar Task 1.) Sigue el mismo estilo SQL (VARCHAR(36) para ids, timestamptz, indices explicitos).

- [ ] **Step 1: Escribir la migracion**

```sql
CREATE TABLE family_section_activity (
    family_id VARCHAR(36) NOT NULL,
    section VARCHAR(20) NOT NULL,
    last_activity_at timestamptz NOT NULL,
    PRIMARY KEY (family_id, section),
    CONSTRAINT fk_family_section_activity_family FOREIGN KEY (family_id) REFERENCES families (id)
);

CREATE TABLE user_section_last_seen (
    user_id VARCHAR(36) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    section VARCHAR(20) NOT NULL,
    last_seen_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, family_id, section),
    CONSTRAINT fk_user_section_last_seen_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_section_last_seen_family FOREIGN KEY (family_id) REFERENCES families (id)
);
```

Verifica antes de escribir que las tablas `families`/`users` referenciadas existen con esos nombres exactos — confirmalo con `grep -n "CREATE TABLE families\|CREATE TABLE users" backend/src/main/resources/db/migration/*.sql` antes de continuar, en vez de asumir.

- [ ] **Step 2: Enum de seccion**

```java
package org.gipsybuho.recetasfamiliares.activity;

public enum FamilySection {
    RECIPE,
    NOTE,
    STOCK
}
```

- [ ] **Step 3: Entidad `family_section_activity`**

```java
package org.gipsybuho.recetasfamiliares.activity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "family_section_activity")
@IdClass(FamilySectionActivityEntity.Key.class)
public class FamilySectionActivityEntity {

    @Id
    @Column(name = "family_id")
    private String familyId;

    @Id
    @Enumerated(EnumType.STRING)
    private FamilySection section;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected FamilySectionActivityEntity() {
    }

    public FamilySectionActivityEntity(String familyId, FamilySection section, Instant lastActivityAt) {
        this.familyId = familyId;
        this.section = section;
        this.lastActivityAt = lastActivityAt;
    }

    public String getFamilyId() {
        return familyId;
    }

    public FamilySection getSection() {
        return section;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void touch(Instant now) {
        this.lastActivityAt = now;
    }

    public static class Key implements java.io.Serializable {
        private String familyId;
        private FamilySection section;

        public Key() {
        }

        public Key(String familyId, FamilySection section) {
            this.familyId = familyId;
            this.section = section;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return familyId.equals(key.familyId) && section == key.section;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(familyId, section);
        }
    }
}
```

- [ ] **Step 4: Repositorio**

```java
package org.gipsybuho.recetasfamiliares.activity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilySectionActivityRepository
        extends JpaRepository<FamilySectionActivityEntity, FamilySectionActivityEntity.Key> {

    Optional<FamilySectionActivityEntity> findByFamilyIdAndSection(String familyId, FamilySection section);
}
```

- [ ] **Step 5: Entidad `user_section_last_seen`**

```java
package org.gipsybuho.recetasfamiliares.activity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_section_last_seen")
@IdClass(UserSectionLastSeenEntity.Key.class)
public class UserSectionLastSeenEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "family_id")
    private String familyId;

    @Id
    @Enumerated(EnumType.STRING)
    private FamilySection section;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected UserSectionLastSeenEntity() {
    }

    public UserSectionLastSeenEntity(String userId, String familyId, FamilySection section, Instant lastSeenAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.section = section;
        this.lastSeenAt = lastSeenAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public FamilySection getSection() {
        return section;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

    public static class Key implements java.io.Serializable {
        private String userId;
        private String familyId;
        private FamilySection section;

        public Key() {
        }

        public Key(String userId, String familyId, FamilySection section) {
            this.userId = userId;
            this.familyId = familyId;
            this.section = section;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return userId.equals(key.userId) && familyId.equals(key.familyId) && section == key.section;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(userId, familyId, section);
        }
    }
}
```

- [ ] **Step 6: Repositorio**

```java
package org.gipsybuho.recetasfamiliares.activity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSectionLastSeenRepository
        extends JpaRepository<UserSectionLastSeenEntity, UserSectionLastSeenEntity.Key> {

    Optional<UserSectionLastSeenEntity> findByUserIdAndFamilyIdAndSection(
            String userId, String familyId, FamilySection section);
}
```

- [ ] **Step 7: Compilar**

Run: `cd backend && mvn -q compile`
Expected: `BUILD SUCCESS`, sin errores.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V20__create_family_activity_schema.sql backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySection.java backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySectionActivityEntity.java backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilySectionActivityRepository.java backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/UserSectionLastSeenEntity.java backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/UserSectionLastSeenRepository.java
git commit -m "feat(backend): esquema de avisos de actividad familiar (migracion + entidades)"
```

---

### Task 2: `FamilyActivityService` con tests TDD

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityService.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityServiceTest.java`

Unico punto de escritura en ambas tablas. `recordActivity` se llama desde los 3 servicios de dominio (Task 3-5). `unseenSections`/`markSeen` los usara el controller (Task 6).

- [ ] **Step 1: Escribir los tests (mockeando los 2 repositorios)**

```java
package org.gipsybuho.recetasfamiliares.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class FamilyActivityServiceTest {

    private static final String FAMILY_ID = "fam-1";
    private static final String ACTOR_ID = "user-actor";
    private static final String OTHER_USER_ID = "user-other";

    private FamilySectionActivityRepository activityRepository;
    private UserSectionLastSeenRepository lastSeenRepository;
    private FamilyActivityService service;

    @BeforeEach
    void setUp() {
        activityRepository = Mockito.mock(FamilySectionActivityRepository.class);
        lastSeenRepository = Mockito.mock(UserSectionLastSeenRepository.class);
        service = new FamilyActivityService(activityRepository, lastSeenRepository);
    }

    @Test
    void recordActivityCreatesRowWhenNoneExistsAndMarksActorAsSeen() {
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.empty());
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(ACTOR_ID, FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.empty());

        service.recordActivity(FAMILY_ID, FamilySection.RECIPE, ACTOR_ID);

        ArgumentCaptor<FamilySectionActivityEntity> activityCaptor =
                ArgumentCaptor.forClass(FamilySectionActivityEntity.class);
        verify(activityRepository).save(activityCaptor.capture());
        assertEquals(FAMILY_ID, activityCaptor.getValue().getFamilyId());
        assertEquals(FamilySection.RECIPE, activityCaptor.getValue().getSection());

        ArgumentCaptor<UserSectionLastSeenEntity> seenCaptor =
                ArgumentCaptor.forClass(UserSectionLastSeenEntity.class);
        verify(lastSeenRepository).save(seenCaptor.capture());
        assertEquals(ACTOR_ID, seenCaptor.getValue().getUserId());
    }

    @Test
    void recordActivityUpdatesExistingRowInPlace() {
        FamilySectionActivityEntity existing =
                new FamilySectionActivityEntity(FAMILY_ID, FamilySection.NOTE, Instant.EPOCH);
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.NOTE))
                .thenReturn(Optional.of(existing));
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(ACTOR_ID, FAMILY_ID, FamilySection.NOTE))
                .thenReturn(Optional.empty());

        service.recordActivity(FAMILY_ID, FamilySection.NOTE, ACTOR_ID);

        assertTrue(existing.getLastActivityAt().isAfter(Instant.EPOCH));
        verify(activityRepository).save(existing);
    }

    @Test
    void markSeenUpdatesExistingRowInPlace() {
        UserSectionLastSeenEntity existing =
                new UserSectionLastSeenEntity(ACTOR_ID, FAMILY_ID, FamilySection.STOCK, Instant.EPOCH);
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(ACTOR_ID, FAMILY_ID, FamilySection.STOCK))
                .thenReturn(Optional.of(existing));

        service.markSeen(FAMILY_ID, FamilySection.STOCK, ACTOR_ID);

        assertTrue(existing.getLastSeenAt().isAfter(Instant.EPOCH));
        verify(lastSeenRepository).save(existing);
    }

    @Test
    void unseenSectionsReturnsTrueWhenNeverSeenButActivityExists() {
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.of(new FamilySectionActivityEntity(FAMILY_ID, FamilySection.RECIPE, Instant.now())));
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.NOTE))
                .thenReturn(Optional.empty());
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.STOCK))
                .thenReturn(Optional.empty());
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(OTHER_USER_ID, FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.empty());

        Map<FamilySection, Boolean> result = service.unseenSections(FAMILY_ID, OTHER_USER_ID);

        assertTrue(result.get(FamilySection.RECIPE));
        assertFalse(result.get(FamilySection.NOTE));
        assertFalse(result.get(FamilySection.STOCK));
    }

    @Test
    void unseenSectionsReturnsFalseWhenLastSeenIsAfterLastActivity() {
        Instant activityTime = Instant.now().minusSeconds(60);
        Instant seenTime = Instant.now();
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.of(new FamilySectionActivityEntity(FAMILY_ID, FamilySection.RECIPE, activityTime)));
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.NOTE)).thenReturn(Optional.empty());
        when(activityRepository.findByFamilyIdAndSection(FAMILY_ID, FamilySection.STOCK)).thenReturn(Optional.empty());
        when(lastSeenRepository.findByUserIdAndFamilyIdAndSection(OTHER_USER_ID, FAMILY_ID, FamilySection.RECIPE))
                .thenReturn(Optional.of(new UserSectionLastSeenEntity(OTHER_USER_ID, FAMILY_ID, FamilySection.RECIPE, seenTime)));

        Map<FamilySection, Boolean> result = service.unseenSections(FAMILY_ID, OTHER_USER_ID);

        assertFalse(result.get(FamilySection.RECIPE));
    }
}
```

- [ ] **Step 2: Correr para confirmar RED**

Run: `cd backend && mvn -q test -Dtest=FamilyActivityServiceTest`
Expected: FALLA en compilacion (`FamilyActivityService` no existe).

- [ ] **Step 3: Implementar el servicio**

```java
package org.gipsybuho.recetasfamiliares.activity;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unico punto de escritura de actividad de familia (avisos de recetas/notas/
 * stock, item 20 del roadmap). "No visto" se calcula comparando timestamps,
 * sin contar eventos individuales -- mismo criterio que el fix aplicado hoy
 * en PrivateInboxPing (contar eventos permite sobre-contar en ediciones).
 */
@Service
public class FamilyActivityService {

    private final FamilySectionActivityRepository activityRepository;
    private final UserSectionLastSeenRepository lastSeenRepository;

    public FamilyActivityService(
            FamilySectionActivityRepository activityRepository,
            UserSectionLastSeenRepository lastSeenRepository
    ) {
        this.activityRepository = activityRepository;
        this.lastSeenRepository = lastSeenRepository;
    }

    /**
     * Registra actividad nueva en una seccion de una familia. El propio autor
     * del cambio queda marcado como "visto" en la misma operacion -- nunca ve
     * su propio badge encenderse por su propio cambio.
     */
    @Transactional
    public void recordActivity(String familyId, FamilySection section, String actorUserId) {
        Instant now = Instant.now();
        FamilySectionActivityEntity activity = activityRepository
                .findByFamilyIdAndSection(familyId, section)
                .orElseGet(() -> new FamilySectionActivityEntity(familyId, section, now));
        activity.touch(now);
        activityRepository.save(activity);
        markSeenAt(familyId, section, actorUserId, now);
    }

    /** Marca una seccion como vista por el usuario, al momento de abrirla. */
    @Transactional
    public void markSeen(String familyId, FamilySection section, String userId) {
        markSeenAt(familyId, section, userId, Instant.now());
    }

    private void markSeenAt(String familyId, FamilySection section, String userId, Instant when) {
        UserSectionLastSeenEntity seen = lastSeenRepository
                .findByUserIdAndFamilyIdAndSection(userId, familyId, section)
                .orElseGet(() -> new UserSectionLastSeenEntity(userId, familyId, section, when));
        seen.touch(when);
        lastSeenRepository.save(seen);
    }

    /** Para cada una de las 3 secciones, si hay actividad no vista por este usuario. */
    @Transactional(readOnly = true)
    public Map<FamilySection, Boolean> unseenSections(String familyId, String userId) {
        Map<FamilySection, Boolean> result = new EnumMap<>(FamilySection.class);
        for (FamilySection section : FamilySection.values()) {
            Instant lastActivity = activityRepository.findByFamilyIdAndSection(familyId, section)
                    .map(FamilySectionActivityEntity::getLastActivityAt)
                    .orElse(null);
            if (lastActivity == null) {
                result.put(section, false);
                continue;
            }
            Instant lastSeen = lastSeenRepository.findByUserIdAndFamilyIdAndSection(userId, familyId, section)
                    .map(UserSectionLastSeenEntity::getLastSeenAt)
                    .orElse(Instant.EPOCH);
            result.put(section, lastActivity.isAfter(lastSeen));
        }
        return result;
    }
}
```

- [ ] **Step 4: Correr para confirmar GREEN**

Run: `cd backend && mvn -q test -Dtest=FamilyActivityServiceTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`, `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityService.java backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityServiceTest.java
git commit -m "feat(backend): FamilyActivityService con tests (upsert, auto-actor, calculo no-visto)"
```

---

### Task 3: Instrumentar `RecipeService`

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/recipes/RecipeService.java`

`createRecipe`/`updateRecipe`/`deleteRecipe` ya reciben `userId` como parametro (confirmado leyendo el archivo real) — solo hace falta inyectar `FamilyActivityService` y llamar `recordActivity` tras cada operacion exitosa.

- [ ] **Step 1: Añadir el campo y el constructor**

Modifica el constructor existente (linea 34-50) para recibir `FamilyActivityService`:

```java
    private final FamilyActivityService familyActivityService;

    public RecipeService(
            RecipeRepository recipeRepository,
            RecipeIngredientRepository ingredientRepository,
            RecipeStepRepository stepRepository,
            RecipePhotoRepository photoRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            UserRepository userRepository,
            org.gipsybuho.recetasfamiliares.activity.FamilyActivityService familyActivityService
    ) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.familyActivityService = familyActivityService;
    }
```

- [ ] **Step 2: Instrumentar los 3 metodos**

```java
    @Transactional
    public RecipeResponse createRecipe(String familyId, String userId, CreateRecipeRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        UserEntity creator = requireUser(userId);
        RecipeEntity recipe = new RecipeEntity(
                family,
                creator,
                request.title().trim(),
                request.description() == null ? null : request.description().trim(),
                request.servings(),
                request.prepMinutes(),
                request.cookMinutes(),
                request.difficulty()
        );
        RecipeResponse response = toResponse(recipeRepository.save(recipe));
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.RECIPE, userId);
        return response;
    }
```

```java
    @Transactional
    public RecipeResponse updateRecipe(String familyId, String recipeId, String userId, UpdateRecipeRequest request) {
        requireEditor(familyId, userId);
        RecipeEntity recipe = requireActiveRecipe(familyId, recipeId);
        recipe.update(
                request.title().trim(),
                request.description() == null ? null : request.description().trim(),
                request.servings(),
                request.prepMinutes(),
                request.cookMinutes(),
                request.difficulty()
        );
        RecipeResponse response = toResponse(recipeRepository.save(recipe));
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.RECIPE, userId);
        return response;
    }
```

```java
    @Transactional
    public void deleteRecipe(String familyId, String recipeId, String userId) {
        requireEditor(familyId, userId);
        RecipeEntity recipe = requireActiveRecipe(familyId, recipeId);
        ingredientRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipeIngredientEntity::softDelete);
        stepRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipeStepEntity::softDelete);
        photoRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipePhotoEntity::softDelete);
        recipe.softDelete();
        recipeRepository.save(recipe);
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.RECIPE, userId);
    }
```

- [ ] **Step 3: Actualizar `RecipeServiceTest.java`**

Lee `backend/src/test/java/org/gipsybuho/recetasfamiliares/recipes/RecipeServiceTest.java` primero para confirmar como construye `RecipeService` en sus tests (mock de cada dependencia del constructor) — añade un mock de `FamilyActivityService` al constructor existente en el `@BeforeEach`, sin cambiar ninguna aserción existente (esas 3 llamadas nuevas a `recordActivity` no necesitan verificarse ahi, ya estan cubiertas por el test de integracion del controller, Task 6).

- [ ] **Step 4: Compilar y correr**

Run: `cd backend && mvn -q test -Dtest=RecipeServiceTest`
Expected: `BUILD SUCCESS`, mismos tests que antes, todos pasan (el mock nuevo no rompe nada).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/recipes/RecipeService.java backend/src/test/java/org/gipsybuho/recetasfamiliares/recipes/RecipeServiceTest.java
git commit -m "feat(backend): RecipeService registra actividad de familia en crear/editar/borrar"
```

---

### Task 4: Instrumentar `FamilyNoteService`

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/notes/FamilyNoteService.java`

Mismo patron exacto que Task 3, aplicado a `createNote`/`updateNote`/`deleteNote` (firmas ya confirmadas: las 3 reciben `userId`).

- [ ] **Step 1: Añadir el campo `FamilyActivityService` al constructor existente** (mismo patron: nuevo parametro final en el constructor, asignado en el cuerpo).

- [ ] **Step 2: Instrumentar los 3 metodos**

```java
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
        FamilyNoteResponse response = toResponse(noteRepository.save(note));
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.NOTE, userId);
        return response;
    }

    @Transactional
    public FamilyNoteResponse updateNote(String familyId, String noteId, String userId, UpdateFamilyNoteRequest request) {
        requireEditor(familyId, userId);
        FamilyNoteEntity note = requireActiveNote(familyId, noteId);
        RecipeEntity recipe = resolveActiveRecipe(familyId, request.recipeId());
        note.update(recipe, request.title().trim(), request.body().trim(), request.pinned());
        FamilyNoteResponse response = toResponse(noteRepository.save(note));
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.NOTE, userId);
        return response;
    }

    @Transactional
    public void deleteNote(String familyId, String noteId, String userId) {
        requireEditor(familyId, userId);
        FamilyNoteEntity note = requireActiveNote(familyId, noteId);
        note.softDelete();
        noteRepository.save(note);
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.NOTE, userId);
    }
```

- [ ] **Step 3: Actualizar los tests existentes de `FamilyNoteService`** (si existen: buscar `find backend/src/test -iname "*FamilyNoteService*"`) mockeando el nuevo parametro del constructor, mismo criterio que Task 3.

- [ ] **Step 4: Compilar y correr**

Run: `cd backend && mvn -q compile test-compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/notes/FamilyNoteService.java
git commit -m "feat(backend): FamilyNoteService registra actividad de familia en crear/editar/borrar"
```

---

### Task 5: Instrumentar `StockItemService`

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/stock/StockItemService.java`

Mismo patron, aplicado a `createStockItem`/`updateStockItem`/`deleteStockItem`.

- [ ] **Step 1: Añadir el campo `FamilyActivityService` al constructor existente.**

- [ ] **Step 2: Instrumentar los 3 metodos**

```java
    @Transactional
    public StockItemResponse createStockItem(String familyId, String userId, CreateStockItemRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        StockItemEntity stockItem = new StockItemEntity(
                family,
                request.name().trim(),
                request.quantity(),
                trimToNull(request.unit()),
                request.lowStockThreshold(),
                request.expiresAt(),
                trimToNull(request.note())
        );
        StockItemResponse response = toResponse(stockItemRepository.save(stockItem));
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.STOCK, userId);
        return response;
    }

    @Transactional
    public StockItemResponse updateStockItem(
            String familyId,
            String stockItemId,
            String userId,
            UpdateStockItemRequest request
    ) {
        requireEditor(familyId, userId);
        StockItemEntity stockItem = requireActiveStockItem(familyId, stockItemId);
        stockItem.update(
                request.name().trim(),
                request.quantity(),
                trimToNull(request.unit()),
                request.lowStockThreshold(),
                request.expiresAt(),
                trimToNull(request.note())
        );
        StockItemResponse response = toResponse(stockItemRepository.save(stockItem));
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.STOCK, userId);
        return response;
    }

    @Transactional
    public void deleteStockItem(String familyId, String stockItemId, String userId) {
        requireEditor(familyId, userId);
        StockItemEntity stockItem = requireActiveStockItem(familyId, stockItemId);
        stockItem.softDelete();
        stockItemRepository.save(stockItem);
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.STOCK, userId);
    }
```

- [ ] **Step 3: Actualizar tests existentes** que construyan `StockItemService` directamente, mismo criterio que Task 3.

- [ ] **Step 4: Compilar y correr**

Run: `cd backend && mvn -q compile test-compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/stock/StockItemService.java
git commit -m "feat(backend): StockItemService registra actividad de familia en crear/editar/borrar"
```

---

### Task 6: Endpoints REST + tests de integracion

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityResponse.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityController.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityControllerTest.java`

DTO con 3 campos boolean nombrados (no un `Map<String,Boolean>` generico — mas simple y type-safe en ambos clientes, consistente con el resto de DTOs del proyecto).

- [ ] **Step 1: DTO de respuesta**

```java
package org.gipsybuho.recetasfamiliares.activity;

public record FamilyActivityResponse(boolean recipe, boolean note, boolean stock) {

    static FamilyActivityResponse from(java.util.Map<FamilySection, Boolean> unseen) {
        return new FamilyActivityResponse(
                Boolean.TRUE.equals(unseen.get(FamilySection.RECIPE)),
                Boolean.TRUE.equals(unseen.get(FamilySection.NOTE)),
                Boolean.TRUE.equals(unseen.get(FamilySection.STOCK))
        );
    }
}
```

- [ ] **Step 2: Escribir el test de integracion primero (TDD)**

Lee `backend/src/test/java/org/gipsybuho/recetasfamiliares/recipes/RecipeControllerTest.java` (ya migrado hoy a `uniqueEmail()`) para el patron exacto de `register(email, familyName)`/`RegisteredUser` — sigue ese MISMO patron, no inventes uno nuevo.

```java
package org.gipsybuho.recetasfamiliares.activity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class FamilyActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allSectionsFalseWhenNoActivityYet() throws Exception {
        RegisteredUser user = register(uniqueEmail("activity-empty"), "Familia Activity Empty");

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe").value(false))
                .andExpect(jsonPath("$.note").value(false))
                .andExpect(jsonPath("$.stock").value(false));
    }

    @Test
    void creatingRecipeMarksItUnseenForOtherMemberButNotForAuthor() throws Exception {
        RegisteredUser owner = register(uniqueEmail("activity-owner"), "Familia Activity");
        String guestEmail = uniqueEmail("activity-guest");
        RegisteredUser guest = register(guestEmail, "Familia Activity Guest");
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Tarta", "servings": 4, "prepMinutes": 10, "cookMinutes": 20, "difficulty": "EASY"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe").value(false));

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe").value(true));
    }

    @Test
    void markingSeenClearsTheFlag() throws Exception {
        RegisteredUser owner = register(uniqueEmail("activity-seen-owner"), "Familia Activity Seen");
        String guestEmail = uniqueEmail("activity-seen-guest");
        RegisteredUser guest = register(guestEmail, "Familia Activity Seen Guest");
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/families/{familyId}/notes", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Nota", "body": "cuerpo", "pinned": false}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/{familyId}/activity/NOTE/seen", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(false));
    }

    @Test
    void activityRequiresFamilyMembership() throws Exception {
        RegisteredUser owner = register(uniqueEmail("activity-forbidden-owner"), "Familia Forbidden");
        RegisteredUser outsider = register(uniqueEmail("activity-forbidden-outsider"), "Familia Ajena");

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", owner.familyId())
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }

    private record RegisteredUser(String accessToken, String familyId, String userId) {}

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Test User",
                                  "password": "very-secure-password",
                                  "familyName": "%s"
                                }
                                """.formatted(email, familyName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new RegisteredUser(
                response.get("accessToken").asText(),
                response.get("family").get("id").asText(),
                response.get("user").get("id").asText()
        );
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
```

- [ ] **Step 3: Correr para confirmar RED**

Run: `cd backend && mvn -q test -Dtest=FamilyActivityControllerTest`
Expected: FALLA en compilacion (`FamilyActivityController` no existe).

- [ ] **Step 4: Implementar el controller**

Antes de escribir, confirma con `grep -n "requireMembership\|existsByFamily_IdAndUser_IdAndDeletedFalse" backend/src/main/java/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepository.java` el nombre exacto del metodo de membership ya usado en el resto del proyecto para no inventarlo.

```java
package org.gipsybuho.recetasfamiliares.activity;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/families/{familyId}/activity")
public class FamilyActivityController {

    private final FamilyActivityService activityService;
    private final FamilyMemberRepository familyMemberRepository;

    public FamilyActivityController(
            FamilyActivityService activityService,
            FamilyMemberRepository familyMemberRepository
    ) {
        this.activityService = activityService;
        this.familyMemberRepository = familyMemberRepository;
    }

    @GetMapping
    public FamilyActivityResponse getActivity(@PathVariable String familyId, Authentication authentication) {
        String userId = authentication.getName();
        requireMembership(familyId, userId);
        return FamilyActivityResponse.from(activityService.unseenSections(familyId, userId));
    }

    @PostMapping("/{section}/seen")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void markSeen(
            @PathVariable String familyId,
            @PathVariable String section,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        requireMembership(familyId, userId);
        FamilySection parsed;
        try {
            parsed = FamilySection.valueOf(section.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown section");
        }
        activityService.markSeen(familyId, parsed, userId);
    }

    private void requireMembership(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found");
        }
    }
}
```

- [ ] **Step 5: Correr para confirmar GREEN**

Run: `cd backend && mvn -q test -Dtest=FamilyActivityControllerTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityResponse.java backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityController.java backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityControllerTest.java
git commit -m "feat(backend): endpoints REST de avisos de actividad familiar (GET + POST seen)"
```

---

### Task 7: Extender `ChatStompAuthChannelInterceptor` (autorizacion del topic)

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptor.java`
- Modify: `backend/src/test/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptorTest.java`

Solo anadir el sufijo `/activity` a los ya reconocidos por `extractFamilyId` — `authorizeFamilyTopic` ya valida membership generico para cualquier sufijo reconocido, sin logica nueva.

- [ ] **Step 1: Test primero (TDD)** — añadir al final de la clase de test, antes del cierre:

```java
    @Test
    void allowsSubscribeToActivityTopicForFamilyMember() {
        when(jwtService.validateAndGetUserId("good-token")).thenReturn(USER_ID);
        Message<byte[]> connect = connect("Bearer good-token");
        interceptor.preSend(connect, channel);
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(FAMILY_ID, USER_ID))
                .thenReturn(true);

        Message<byte[]> subscribe = subscribe("/topic/families/" + FAMILY_ID + "/activity");
        assertDoesNotThrow(() -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeToActivityTopicForNonMember() {
        when(jwtService.validateAndGetUserId("good-token")).thenReturn(USER_ID);
        Message<byte[]> connect = connect("Bearer good-token");
        interceptor.preSend(connect, channel);
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(FAMILY_ID, USER_ID))
                .thenReturn(false);

        Message<byte[]> subscribe = subscribe("/topic/families/" + FAMILY_ID + "/activity");
        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
    }
```

**Antes de pegar esto**, lee el archivo de test completo para confirmar los helpers `connect(String)`/`subscribe(String)` existentes (nombres/firmas exactas) y usa esos, no inventes nuevos.

- [ ] **Step 2: Correr para confirmar RED**

Run: `cd backend && mvn -q test -Dtest=ChatStompAuthChannelInterceptorTest`
Expected: FALLA (destino `/activity` no reconocido, cae al `else` de `authorizeSubscription` -> `MessagingException` incluso para miembro valido).

- [ ] **Step 3: Extender el interceptor**

```java
    private static final String ACTIVITY_SUFFIX = "/activity";
```

(añadir junto a `CHAT_SUFFIX`/`PRESENCE_SUFFIX`, linea ~45)

Modifica `extractFamilyId` (lineas 166-180) para reconocer el nuevo sufijo:

```java
    private String extractFamilyId(String destination) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        String suffix;
        if (destination.endsWith(CHAT_SUFFIX)) {
            suffix = CHAT_SUFFIX;
        } else if (destination.endsWith(PRESENCE_SUFFIX)) {
            suffix = PRESENCE_SUFFIX;
        } else if (destination.endsWith(ACTIVITY_SUFFIX)) {
            suffix = ACTIVITY_SUFFIX;
        } else {
            return null;
        }
        String familyId = destination.substring(TOPIC_PREFIX.length(), destination.length() - suffix.length());
        return familyId.isBlank() ? null : familyId;
    }
```

`authorizeFamilyTopic` (lineas 121-133) no necesita cambios — ya valida membership generico y solo hace logica adicional (`presenceRegistry.subscribe`) si el sufijo es especificamente `PRESENCE_SUFFIX`, que seguira intacto.

- [ ] **Step 4: Correr para confirmar GREEN**

Run: `cd backend && mvn -q test -Dtest=ChatStompAuthChannelInterceptorTest`
Expected: todos los tests (los previos + los 2 nuevos) pasan.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptor.java backend/src/test/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptorTest.java
git commit -m "feat(backend): autoriza el topic STOMP de actividad familiar (mismo patron que chat/presence)"
```

---

### Task 8: Publisher de tiempo real + wiring en los 3 servicios

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityRealtimePublisher.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityRealtimePublisherTest.java`
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityService.java`

Mismo patron que `PresencePublisher`/`PrivateConversationRealtimePublisher` ya existentes. Se invoca desde `FamilyActivityService.recordActivity` (unico punto de escritura), no desde los 3 servicios de dominio directamente — asi ningun futuro llamador de `recordActivity` puede olvidarse de emitir el ping.

- [ ] **Step 1: Test primero**

```java
package org.gipsybuho.recetasfamiliares.activity;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class FamilyActivityRealtimePublisherTest {

    @Test
    void publishesSectionOnlyNoContent() {
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        FamilyActivityRealtimePublisher publisher = new FamilyActivityRealtimePublisher(messagingTemplate);

        publisher.publish("fam-1", FamilySection.RECIPE);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/families/fam-1/activity"),
                eq(new FamilyActivityPing(FamilySection.RECIPE)));
    }
}
```

- [ ] **Step 2: Correr para confirmar RED**

Run: `cd backend && mvn -q test -Dtest=FamilyActivityRealtimePublisherTest`
Expected: falla, `FamilyActivityRealtimePublisher`/`FamilyActivityPing` no existen.

- [ ] **Step 3: Implementar el ping (record) y el publisher**

```java
package org.gipsybuho.recetasfamiliares.activity;

public record FamilyActivityPing(FamilySection section) {
}
```

```java
package org.gipsybuho.recetasfamiliares.activity;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Avisa en vivo que una seccion de la familia tiene actividad nueva. Sin contenido del cambio. */
@Component
public class FamilyActivityRealtimePublisher {

    static final String TOPIC_PREFIX = "/topic/families/";
    static final String TOPIC_SUFFIX = "/activity";

    private final SimpMessagingTemplate messagingTemplate;

    public FamilyActivityRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    static String topicFor(String familyId) {
        return TOPIC_PREFIX + familyId + TOPIC_SUFFIX;
    }

    public void publish(String familyId, FamilySection section) {
        messagingTemplate.convertAndSend(topicFor(familyId), new FamilyActivityPing(section));
    }
}
```

- [ ] **Step 4: Correr para confirmar GREEN**

Run: `cd backend && mvn -q test -Dtest=FamilyActivityRealtimePublisherTest`
Expected: `Tests run: 1, Failures: 0`.

- [ ] **Step 5: Conectar el publisher en `FamilyActivityService.recordActivity`**

```java
    private final FamilyActivityRealtimePublisher realtimePublisher;

    public FamilyActivityService(
            FamilySectionActivityRepository activityRepository,
            UserSectionLastSeenRepository lastSeenRepository,
            FamilyActivityRealtimePublisher realtimePublisher
    ) {
        this.activityRepository = activityRepository;
        this.lastSeenRepository = lastSeenRepository;
        this.realtimePublisher = realtimePublisher;
    }
```

Y al final de `recordActivity`, tras el `markSeenAt` del actor:

```java
    @Transactional
    public void recordActivity(String familyId, FamilySection section, String actorUserId) {
        Instant now = Instant.now();
        FamilySectionActivityEntity activity = activityRepository
                .findByFamilyIdAndSection(familyId, section)
                .orElseGet(() -> new FamilySectionActivityEntity(familyId, section, now));
        activity.touch(now);
        activityRepository.save(activity);
        markSeenAt(familyId, section, actorUserId, now);
        realtimePublisher.publish(familyId, section);
    }
```

Actualiza `FamilyActivityServiceTest.java` (Task 2): el constructor de `FamilyActivityService` en `setUp()` ahora necesita un tercer mock (`FamilyActivityRealtimePublisher`), pasado igual que los otros dos.

- [ ] **Step 6: Compilar y correr TODO el paquete `activity`**

Run: `cd backend && mvn -q test -Dtest='org.gipsybuho.recetasfamiliares.activity.*'`
Expected: `BUILD SUCCESS`, todos los tests del paquete pasan.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityRealtimePublisher.java backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityPing.java backend/src/main/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityService.java backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityRealtimePublisherTest.java backend/src/test/java/org/gipsybuho/recetasfamiliares/activity/FamilyActivityServiceTest.java
git commit -m "feat(backend): publisher de tiempo real de actividad familiar, conectado a recordActivity"
```

---

### Task 9: Validacion backend completa contra la BD real

**Files:** ninguno (validacion)

- [ ] **Step 1: Suite completa contra la BD real de Hetzner**

```bash
cd "C:\Users\GipsyDavy\MAVEN\Recetas Familiares" && set -a && source herztner/recetas_app.env && set +a && cd backend && mvn test
```

Expected: `BUILD SUCCESS`, todos los tests pasan (baseline previo + los nuevos de esta feature), sin regresiones en `RecipeControllerTest`/`FamilyNoteControllerTest` (renombrado real: confirmar el nombre exacto del archivo)/`StockItemControllerTest` tras la instrumentacion.

---

## ANDROID

### Task 10: DTOs + endpoints

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt`

- [ ] **Step 1: DTOs** (añadir al final de `ApiDtos.kt`)

```kotlin

// ── Avisos de actividad familiar ──────────────────────────────────────────

data class FamilyActivityDto(
    val recipe: Boolean,
    val note: Boolean,
    val stock: Boolean
)

data class FamilyActivityPingDto(
    val section: String
)
```

- [ ] **Step 2: Endpoints** (añadir al final de la interfaz, junto a los DTOs de chat privado)

```kotlin
    // ── Avisos de actividad familiar ──────────────────────────────────────────

    @GET("api/v1/families/{familyId}/activity")
    suspend fun familyActivity(@Path("familyId") familyId: String): FamilyActivityDto

    @POST("api/v1/families/{familyId}/activity/{section}/seen")
    suspend fun markSectionSeen(
        @Path("familyId") familyId: String,
        @Path("section") section: String
    ): Response<Unit>
```

Añade el import `org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyActivityDto` junto a los demas imports de DTOs, y confirma que `retrofit2.Response` ya esta importado en el archivo (usado por otros endpoints `@DELETE`/similares) antes de asumirlo.

- [ ] **Step 3: Compilar**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt
git commit -m "feat(android): DTOs y endpoints de avisos de actividad familiar"
```

---

### Task 11: Extender `ChatSocket.kt` — topic de actividad

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt`
- Modify: `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt`

El topic de actividad es FIJO por familia (como `presenceTopic`/`inboxTopic`), siempre suscrito en CONNECTED — a diferencia de `conversationTopic` que es opcional.

- [ ] **Step 1: Test de rutina de frame (pasa de inmediato, documenta el formato)**

Añade a `ChatSocketFrameParsingTest.kt`:

```kotlin

    @Test
    fun extractsDestinationFromActivityFrame() {
        val frame = "MESSAGE\n" +
            "destination:/topic/families/fam-1/activity\n" +
            "subscription:sub-activity\n" +
            "\n" +
            "{\"section\":\"RECIPE\"}"

        assertEquals("/topic/families/fam-1/activity", extractStompHeader(frame, "destination"))
    }
```

- [ ] **Step 2: Correr para confirmar que pasa ya (documenta el formato)**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.ChatSocketFrameParsingTest"`
Expected: pasa (ejercita solo `extractStompHeader`, ya existente).

- [ ] **Step 3: Extender `ChatSocket.kt`**

Añade el import:

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyActivityPingDto
```

Añade el nuevo parametro al constructor (tras `onPrivateMessage`):

```kotlin
    private val onPrivateMessage: (PrivateMessageDto) -> Unit = {},
    private val onActivityPing: (FamilyActivityPingDto) -> Unit = {}
```

Añade el topic fijo (junto a `inboxTopic`):

```kotlin
    private val activityTopic: String = "/topic/families/$familyId/activity"
```

En el bloque `CONNECTED`, añade la suscripcion fija (junto a `subscribeInbox`, antes del `conversationTopic?.let`):

```kotlin
                val subscribeActivity = "SUBSCRIBE\n" +
                    "id:sub-activity\n" +
                    "destination:$activityTopic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribeActivity)
```

En el `when` de `MESSAGE`, añade el branch (antes del `else if (destination == topic)`):

```kotlin
                } else if (destination == activityTopic) {
                    handleActivityPing(body)
```

Añade el handler (junto a `handleInboxPing`):

```kotlin
    private fun handleActivityPing(body: String) {
        runCatching { gson.fromJson(body, FamilyActivityPingDto::class.java) }
            .getOrNull()
            ?.let(onActivityPing)
    }
```

- [ ] **Step 4: Compilar** (fallara — `ChatRepository.kt` no pasa `onActivityPing`, se arregla en Task 12 junto con este)

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (el nuevo parametro tiene default `{}`, no rompe el unico call site existente — a diferencia del `myUserId` de hoy, este SI tiene default, asi que no debería fallar).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt
git commit -m "feat(android): ChatSocket suscribe el topic fijo de actividad familiar"
```

---

### Task 12: Extender `ChatRepository.kt` — exponer `onActivityPing`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt`

Lee el `openRealtime` actual primero (extendido hoy con `onInboxPing`/`onPrivateMessage`) y añade el nuevo parametro con el mismo criterio: pasa directo, sin normalizacion (el ping no lleva URLs de adjuntos que reescribir).

- [ ] **Step 1: Añadir el import**

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyActivityPingDto
```

- [ ] **Step 2: Extender la firma y el cuerpo de `openRealtime`**

```kotlin
    fun openRealtime(
        onMessage: (ChatMessageDto) -> Unit,
        onConnectionChange: (Boolean) -> Unit,
        onPresenceUpdate: (Set<String>) -> Unit,
        conversationId: String? = null,
        onInboxPing: (PrivateInboxPingDto) -> Unit = {},
        onPrivateMessage: (PrivateMessageDto) -> Unit = {},
        onActivityPing: (FamilyActivityPingDto) -> Unit = {}
    ): ChatSocket? {
        val family = familyId ?: return null
        val user = myUserId ?: return null
        val socket = ChatSocket(
            httpClient = httpClient,
            baseUrl = baseUrlProvider(),
            sessionStore = sessionStore,
            familyId = family,
            myUserId = user,
            gson = gson,
            onMessage = { msg -> onMessage(normalizeAttachments(msg)) },
            onConnectionChange = onConnectionChange,
            onPresenceUpdate = onPresenceUpdate,
            conversationId = conversationId,
            onInboxPing = onInboxPing,
            onPrivateMessage = { msg -> onPrivateMessage(normalizePrivateMessage(msg)) },
            onActivityPing = onActivityPing
        )
        socket.connect()
        return socket
    }
```

- [ ] **Step 3: Compilar y correr toda la suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, sin regresiones (el nuevo parametro tiene default, los 2 call sites existentes -- `startChatBadge`/`openChat`/`openPrivateChat` -- siguen compilando sin cambios).

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt
git commit -m "feat(android): ChatRepository.openRealtime expone el ping de actividad familiar"
```

---

### Task 13: Estado en `RecetasViewModel.kt`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt`

Fetch inicial junto a `loadFamilyStats()`, suscripcion al ping via `startChatBadge()` (el socket-siempre-vivo, ya que el aviso debe funcionar incluso sin tener Recetas/Stock/Notas abiertas), y un metodo `markSectionSeen(section)` llamado al navegar a cada tab.

- [ ] **Step 1: Imports**

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyActivityDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyActivityPingDto
```

- [ ] **Step 2: Estado nuevo** — junto al bloque de estado de chat privado (tras `activePrivateConversationId`):

```kotlin

    // ── Avisos de actividad familiar ─────────────────────────────────────────

    private val _sectionsWithUnseenActivity = MutableStateFlow<Set<String>>(emptySet())
    val sectionsWithUnseenActivity: StateFlow<Set<String>> = _sectionsWithUnseenActivity.asStateFlow()
```

- [ ] **Step 3: Fetch inicial** — extiende `loadFamilyStats()` (linea 545-553) para tambien cargar actividad, o añade un metodo hermano llamado desde el mismo punto donde se llama `loadFamilyStats()`:

```kotlin
    fun loadFamilyActivity() {
        viewModelScope.launch {
            val familyId = container.sessionStore.familyId ?: return@launch
            runCatching { container.familyMemberRepository.familyActivity() }
                .onSuccess {
                    if (familyId == container.sessionStore.familyId) applyActivitySnapshot(it)
                }
        }
    }

    private fun applyActivitySnapshot(activity: FamilyActivityDto) {
        val unseen = buildSet {
            if (activity.recipe) add("RECIPE")
            if (activity.note) add("NOTE")
            if (activity.stock) add("STOCK")
        }
        _sectionsWithUnseenActivity.value = unseen
    }
```

Confirma con `grep -n "familyActivity\|class FamilyMemberRepository" android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/Repositories.kt` si `familyActivity()` ya existe como metodo de `FamilyMemberRepository` — si no, añadelo ahi mismo (mismo repositorio que ya expone `stats()`/`members()`):

```kotlin
    suspend fun familyActivity(): FamilyActivityDto {
        val familyId = sessionStore.familyId ?: throw IllegalStateException("No family in session")
        return api.familyActivity(familyId)
    }

    suspend fun markSectionSeen(section: String) {
        val familyId = sessionStore.familyId ?: throw IllegalStateException("No family in session")
        api.markSectionSeen(familyId, section)
    }
```

(añadir estos 2 metodos a la misma clase `FamilyMemberRepository` en `Repositories.kt` que ya expone `stats()`/`members()` — confirma el nombre exacto de sus campos `api`/`sessionStore` antes de escribir, deberian coincidir con el resto de metodos de esa clase).

- [ ] **Step 4: Llamar `loadFamilyActivity()` en el mismo punto donde ya se llama `loadFamilyStats()`** — busca cada call site de `loadFamilyStats()` (con `grep -n "loadFamilyStats()" android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt`) y añade `viewModel.loadFamilyActivity()` justo despues de cada uno.

- [ ] **Step 5: Suscribir el ping en tiempo real, extendiendo `startChatBadge()`**

```kotlin
    fun startChatBadge() {
        if (chatBadgeSocket != null || !_isLoggedIn.value) return
        chatBadgeSocket = container.chatRepository.openRealtime(
            onMessage = { msg ->
                val firstTime = chatBadgeSeenIds.add(msg.id)
                val fromOther = msg.authorUserId != null && msg.authorUserId != myUserId
                if (firstTime && !chatScreenOpen && fromOther && !msg.deleted) {
                    _chatUnread.update { it + 1 }
                }
            },
            onConnectionChange = {},
            onPresenceUpdate = { online -> _onlineUserIds.value = online },
            onInboxPing = { ping -> handlePrivateInboxPing(ping) },
            onActivityPing = { ping -> handleActivityPing(ping) }
        )
    }

    private fun handleActivityPing(ping: FamilyActivityPingDto) {
        _sectionsWithUnseenActivity.update { it + ping.section }
    }
```

- [ ] **Step 6: Metodo para marcar visto al navegar**

```kotlin
    fun markSectionSeen(section: String) {
        if (!_sectionsWithUnseenActivity.value.contains(section)) return
        _sectionsWithUnseenActivity.update { it - section }
        viewModelScope.launch {
            runCatching { container.familyMemberRepository.markSectionSeen(section) }
        }
    }
```

- [ ] **Step 7: Limpiar el estado en `stopChatBadge()`** (mismo criterio que `_privateChatUnread`/`_conversations` ya limpiados ahi):

```kotlin
    fun stopChatBadge() {
        chatBadgeSocket?.disconnect()
        chatBadgeSocket = null
        chatBadgeSeenIds.clear()
        _chatUnread.value = 0
        _privateChatUnread.value = emptyMap()
        _conversations.value = emptyList()
        _sectionsWithUnseenActivity.value = emptySet()
    }
```

- [ ] **Step 8: Compilar**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/Repositories.kt
git commit -m "feat(android): estado de avisos de actividad familiar en RecetasViewModel"
```

---

### Task 14: Badge en `NavigationBarItem`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt`

Badge SIN numero (`Badge { }` vacio, Material3 renderiza un punto pequeño sin contenido) sobre RECIPES/STOCK/NOTES. Al cambiar de tab a una de esas 3, marcar vista.

- [ ] **Step 1: Imports** (junto a los ya existentes `Badge`/`BadgedBox`)

Confirma que `Badge`/`BadgedBox` ya estan importados (usados hoy para el chat privado) antes de re-anadirlos.

- [ ] **Step 2: Leer el estado**

Justo antes del bloque `NavigationBar` (linea ~508), añade:

```kotlin
            val sectionsWithUnseenActivity by viewModel.sectionsWithUnseenActivity.collectAsState()
```

- [ ] **Step 3: Envolver los 3 iconos relevantes en `BadgedBox`, y marcar visto en `onClick`**

Reemplaza el bloque `NavigationBar` (lineas 509-522) por:

```kotlin
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainTab.RECIPES,
                    onClick = { tab = MainTab.RECIPES; viewModel.markSectionSeen("RECIPE") },
                    icon = {
                        BadgedBox(badge = { if (sectionsWithUnseenActivity.contains("RECIPE")) Badge() }) {
                            Icon(if (tab == MainTab.RECIPES) Icons.Filled.Restaurant else Icons.Outlined.Restaurant, contentDescription = null)
                        }
                    },
                    label = { Text("Recetas") }
                )
                NavigationBarItem(
                    selected = tab == MainTab.STOCK,
                    onClick = { tab = MainTab.STOCK; viewModel.markSectionSeen("STOCK") },
                    icon = {
                        BadgedBox(badge = { if (sectionsWithUnseenActivity.contains("STOCK")) Badge() }) {
                            Icon(if (tab == MainTab.STOCK) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2, contentDescription = null)
                        }
                    },
                    label = { Text("Stock") }
                )
                NavigationBarItem(selected = tab == MainTab.SHOPPING, onClick = { tab = MainTab.SHOPPING },
                    icon = { Icon(if (tab == MainTab.SHOPPING) Icons.Filled.ShoppingCart else Icons.Outlined.ShoppingCart, contentDescription = null) }, label = { Text("Lista") })
                NavigationBarItem(
                    selected = tab == MainTab.NOTES,
                    onClick = { tab = MainTab.NOTES; viewModel.markSectionSeen("NOTE") },
                    icon = {
                        BadgedBox(badge = { if (sectionsWithUnseenActivity.contains("NOTE")) Badge() }) {
                            Icon(if (tab == MainTab.NOTES) Icons.Filled.Description else Icons.Outlined.Description, contentDescription = null)
                        }
                    },
                    label = { Text("Notas") }
                )
                NavigationBarItem(selected = tab == MainTab.MENU, onClick = { tab = MainTab.MENU },
                    icon = { Icon(if (tab == MainTab.MENU) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth, contentDescription = null) }, label = { Text("Menú") })
                NavigationBarItem(selected = tab == MainTab.PROFILE, onClick = { tab = MainTab.PROFILE },
                    icon = { Icon(if (tab == MainTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = null) }, label = { Text("Perfil") })
            }
```

- [ ] **Step 4: Compilar y correr la suite completa**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt
git commit -m "feat(android): badge sin numero en Recetas/Stock/Notas para avisos de actividad familiar"
```

---

## DESKTOP

### Task 15: DTOs

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/FamilyDtos.java`

- [ ] **Step 1: Leer el archivo primero** para confirmar el estilo exacto de los records ya existentes (`FamilyResponse`, `PresenceResponse`) y añadir los 2 nuevos con el mismo estilo:

```java
    public record FamilyActivityResponse(boolean recipe, boolean note, boolean stock) {
    }

    public record FamilyActivityPing(String section) {
    }
```

- [ ] **Step 2: Compilar**

Run: `cd desktop && mvn -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/FamilyDtos.java
git commit -m "feat(desktop): DTOs de avisos de actividad familiar"
```

---

### Task 16: Extender `ChatSocket.java` — topic de actividad

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java`

Mismo patron que Task 11 en Android: topic fijo por familia, siempre suscrito en CONNECTED.

- [ ] **Step 1: Añadir el campo y el nuevo parametro del constructor**

```java
    private final String activityTopic;
    private final Consumer<org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos.FamilyActivityPing> onActivityPing;
```

Extiende el constructor (linea 74-99) añadiendo el nuevo `Consumer` como ultimo parametro:

```java
    public ChatSocket(
            ApiClient apiClient,
            Supplier<String> tokenSupplier,
            String familyId,
            String myUserId,
            Gson gson,
            Consumer<ChatDtos.ChatMessage> onMessage,
            Consumer<Boolean> onConnectionChange,
            Consumer<Set<String>> onPresenceUpdate,
            Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateInboxPing> onInboxPing,
            Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> onPrivateMessage,
            Consumer<org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos.FamilyActivityPing> onActivityPing
    ) {
        this.apiClient = apiClient;
        this.tokenSupplier = tokenSupplier;
        this.familyId = familyId;
        this.wsUrl = toWebSocketUrl(apiClient.getBaseUrl());
        this.topic = "/topic/families/" + familyId + "/chat";
        this.presenceTopic = "/topic/families/" + familyId + "/presence";
        this.inboxTopic = "/topic/users/" + myUserId + "/inbox";
        this.activityTopic = "/topic/families/" + familyId + "/activity";
        this.gson = gson;
        this.onMessage = onMessage;
        this.onConnectionChange = onConnectionChange;
        this.onPresenceUpdate = onPresenceUpdate;
        this.onInboxPing = onInboxPing;
        this.onPrivateMessage = onPrivateMessage;
        this.onActivityPing = onActivityPing;
    }
```

- [ ] **Step 2: Suscribir en CONNECTED** (junto a `subscribeInbox`, dentro del `case "CONNECTED" ->`):

```java
                String subscribeActivity = "SUBSCRIBE\n"
                        + "id:sub-activity\n"
                        + "destination:" + activityTopic + "\n"
                        + "\n"
                        + NUL;
                socket.send(subscribeActivity);
```

- [ ] **Step 3: Rutear en MESSAGE** (añade el `else if` antes de `topic.equals(destination)`):

```java
                } else if (activityTopic.equals(destination)) {
                    handleActivityPing(body);
```

- [ ] **Step 4: Handler**

```java
    private void handleActivityPing(String body) {
        try {
            var ping = gson.fromJson(body,
                    org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos.FamilyActivityPing.class);
            if (ping != null && ping.section() != null) {
                onActivityPing.accept(ping);
            }
        } catch (RuntimeException ignored) {
            // Frame no parseable: se descarta sin romper la conexion.
        }
    }
```

- [ ] **Step 5: Compilar** (fallara — `ChatRepository.java` no pasa el nuevo argumento; se arregla en Task 17 junto con este)

Run: `cd desktop && mvn -q compile`
Expected: falla con exactamente un error, en la construccion de `ChatSocket` dentro de `ChatRepository.openRealtime` (falta argumento).

- [ ] **Step 6: No commitear todavia** — Task 17 completa el compilado y commitea ambos juntos.

---

### Task 17: Extender `ChatRepository.java` — listener de actividad + endpoints REST

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepository.java`

Mismo patron interno que `inboxListener`/`handleInboxPing` ya existentes: `ChatRepository` mantiene el estado (`Set<String> sectionsWithUnseenActivity`), expone `setActivityListener(...)`, y pasa su propio `this::handleActivityPing` a `ChatSocket` (la UI nunca ve el `ChatSocket` directamente, solo el listener del repositorio).

- [ ] **Step 1: Estado + listener en `ChatRepository.java`** (junto al bloque de `unreadByConversation`/`inboxListener`):

```java
    private final java.util.Set<String> sectionsWithUnseenActivity = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile Consumer<java.util.Set<String>> activityListener;

    public void setActivityListener(Consumer<java.util.Set<String>> listener) {
        this.activityListener = listener;
    }

    public java.util.Set<String> sectionsWithUnseenActivity() {
        return java.util.Set.copyOf(sectionsWithUnseenActivity);
    }

    /** Limpia una seccion localmente al navegar a ella (el POST "seen" real lo hace el llamador). */
    public void markSectionSeenLocally(String section) {
        if (sectionsWithUnseenActivity.remove(section)) {
            notifyActivityListener();
        }
    }

    private void handleActivityPing(org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos.FamilyActivityPing ping) {
        sectionsWithUnseenActivity.add(ping.section());
        notifyActivityListener();
    }

    private void notifyActivityListener() {
        Consumer<java.util.Set<String>> listener = activityListener;
        if (listener != null) {
            listener.accept(sectionsWithUnseenActivity());
        }
    }
```

Añade tambien la limpieza en `resetPrivateChatState()` (ya existente, mismo criterio que `unreadByConversation.clear()`):

```java
    public void resetPrivateChatState() {
        unreadByConversation.clear();
        activeConversationId = null;
        sectionsWithUnseenActivity.clear();
    }
```

- [ ] **Step 2: Pasar el nuevo callback en `openRealtime`**

```java
    public ChatSocket openRealtime(
            Consumer<ChatDtos.ChatMessage> onMessage,
            Consumer<Boolean> onConnectionChange
    ) {
        String family = familyId();
        if (family == null || family.isBlank()) {
            return null;
        }
        ChatSocket socket = new ChatSocket(
                api,
                session::getAccessToken,
                family,
                session.getUserId(),
                gson,
                onMessage,
                onConnectionChange,
                this::handlePresenceUpdate,
                this::handleInboxPing,
                this::handlePrivateMessage,
                this::handleActivityPing);
        socket.connect();
        this.activeSocket = socket;
        return socket;
    }
```

- [ ] **Step 3: Endpoints REST en `FamilyRepository.java`** (junto a `stats()`/`loadPresence()`):

```java
    public FamilyDtos.FamilyActivityResponse loadActivity(String familyId) throws ApiException {
        return api.get("api/v1/families/" + familyId + "/activity", FamilyDtos.FamilyActivityResponse.class);
    }

    public void markSectionSeen(String familyId, String section) throws ApiException {
        api.post("api/v1/families/" + familyId + "/activity/" + section + "/seen", "{}", Void.class);
    }
```

- [ ] **Step 4: Compilar**

Run: `cd desktop && mvn -q compile`
Expected: `BUILD SUCCESS` (cierra el error dejado pendiente en Task 16).

- [ ] **Step 5: Correr los tests de Desktop**

Run: `cd desktop && mvn -q test`
Expected: `BUILD SUCCESS`, sin regresiones.

- [ ] **Step 6: Commit** (Tasks 16+17 juntas, ya que el modulo solo compila con ambas)

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepository.java
git commit -m "feat(desktop): ChatSocket/ChatRepository/FamilyRepository - avisos de actividad familiar"
```

---

### Task 18: UI en `MainWindow.java`

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java`

Indicador SIN numero (a diferencia de `updateChatBadge`/`updatePrivateChatBadge` que si muestran numero): antepone un punto "•" al texto del boton cuando hay actividad no vista, mismo mecanismo de `setText` ya usado.

- [ ] **Step 1: Wiring del listener** (junto a donde ya se registra `setInboxListener`, linea ~160):

```java
        context.getChatRepository().setActivityListener(
                sections -> Platform.runLater(() -> updateActivityBadges(sections)));
```

- [ ] **Step 2: Metodo de actualizacion visual** (junto a `updateChatBadge`/`updatePrivateChatBadge`, linea ~667):

```java
    /** Marca sin numero (punto) en Recetas/Stock/Notas cuando hay actividad sin ver. */
    private void updateActivityBadges(java.util.Set<String> sectionsWithUnseenActivity) {
        applyActivityMark(btnRecipes, "📖  Recetas", sectionsWithUnseenActivity.contains("RECIPE"));
        applyActivityMark(btnStock, "🧂  Stock", sectionsWithUnseenActivity.contains("STOCK"));
        applyActivityMark(btnNotes, "📝  Notas familiares", sectionsWithUnseenActivity.contains("NOTE"));
    }

    private void applyActivityMark(Button button, String baseText, boolean hasUnseenActivity) {
        if (button == null) {
            return;
        }
        button.setText(hasUnseenActivity ? baseText + "  •" : baseText);
    }
```

- [ ] **Step 3: Fetch inicial** — junto a donde se carga `loadPresence`/`stats` al mostrar la ventana principal (busca con `grep -n "loadPresence\|getFamilyRepository().stats" desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java` el punto exacto), añade:

```java
        try {
            var activity = context.getFamilyRepository().loadActivity(context.getSession().getFamilyId());
            java.util.Set<String> unseen = new java.util.HashSet<>();
            if (activity.recipe()) unseen.add("RECIPE");
            if (activity.note()) unseen.add("NOTE");
            if (activity.stock()) unseen.add("STOCK");
            updateActivityBadges(unseen);
        } catch (ApiException e) {
            // Sin bloquear el arranque de la ventana si esta llamada falla.
        }
```

- [ ] **Step 4: Marcar visto al navegar** — dentro de `navigateTo(String view)` (linea 523), añade tras `updateActiveSidebarButton(view);` (linea 536):

```java
        if ("recipes".equals(view)) {
            markSectionSeen("RECIPE");
        } else if ("stock".equals(view)) {
            markSectionSeen("STOCK");
        } else if ("notes".equals(view)) {
            markSectionSeen("NOTE");
        }
```

Y el metodo auxiliar (llama al repo local + dispara el REST en background, sin bloquear el hilo de UI):

```java
    private void markSectionSeen(String section) {
        context.getChatRepository().markSectionSeenLocally(section);
        String familyId = context.getSession().getFamilyId();
        if (familyId == null) {
            return;
        }
        new Thread(() -> {
            try {
                context.getFamilyRepository().markSectionSeen(familyId, section);
            } catch (ApiException ignored) {
                // Best-effort: si falla, el proximo fetch de loadActivity() lo corrige.
            }
        }, "activity-seen").start();
    }
```

- [ ] **Step 5: Compilar y correr**

Run: `cd desktop && mvn -q compile test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java
git commit -m "feat(desktop): indicador sin numero de actividad familiar en Recetas/Stock/Notas"
```

---

## CIERRE

### Task 19: VibeSec sobre el diff completo

**Files:** ninguno (revision)

- [ ] **Step 1: Invocar `/VibeSec`** sobre el diff completo (Tasks 1-18), con foco en:
  - Los 2 endpoints REST (`GET .../activity`, `POST .../activity/{section}/seen`) devuelven 404 (no 403) para no-miembros, mismo patron anti-enumeracion del resto del proyecto.
  - El topic STOMP nuevo reutiliza la autorizacion existente sin logica nueva (Task 7) — confirmar que no se puede suscribir a la actividad de una familia ajena.
  - El ping de tiempo real (`FamilyActivityPing`) nunca lleva el contenido del cambio, solo el enum de seccion.
  - Ningun dato de otra familia se filtra: `unseenSections`/`recordActivity` siempre reciben `familyId` ya validado por `requireMembership`/`requireEditor` en los servicios de dominio existentes.
- [ ] **Step 2: Corregir cualquier hallazgo Critical/Important antes de continuar.** Si no hay hallazgos, documentarlo explicitamente.

---

### Task 20: Validacion final

**Files:** ninguno

- [ ] **Step 1: Backend, suite completa contra la BD real**

```bash
cd "C:\Users\GipsyDavy\MAVEN\Recetas Familiares" && set -a && source herztner/recetas_app.env && set +a && cd backend && mvn test
```

Expected: `BUILD SUCCESS`, 0 fallos nuevos frente al baseline previo a este plan.

- [ ] **Step 2: Android**

```bash
cd android && ./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` en ambos.

- [ ] **Step 3: Desktop**

```bash
cd desktop && mvn test && mvn -DskipTests compile
```

Expected: `BUILD SUCCESS` en ambos.

- [ ] **Step 4: Prueba manual — documentar como pendiente, no simularla**

Bloqueada para el agente en este entorno (requiere dos cuentas y multiples dispositivos/instancias con interaccion tactil/de raton real). Pendiente de que el usuario:
- Con dos cuentas en la misma familia, cree/edite/borre una receta desde una cuenta y confirme que la otra ve el indicador encenderse en Recetas (Android y Desktop).
- Confirme que al abrir esa seccion el indicador se apaga, y que sigue apagado tras reiniciar la app (persistencia real, no solo en memoria).
- Confirme que quien HIZO el cambio nunca ve su propio indicador encendido.
- Repita para Notas y Stock.

- [ ] **Step 5: Actualizar `CONTINUAR.md`** con el cierre de este sprint (agente lider, skills usadas, seguridad ejecutada, archivos, validacion, riesgo residual de la prueba manual pendiente), siguiendo el mismo patron que los cierres anteriores de esta sesion.
