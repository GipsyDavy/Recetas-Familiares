# Chat privado 1:1 — Backend (dm/) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backend completo del chat privado 1:1 entre miembros de una familia: esquema de datos, API REST con paridad funcional al chat familiar (texto, imágenes, editar/borrar propio, limpiar vista, exportar), y dos topics STOMP nuevos (por conversación y por bandeja de usuario) para entrega en tiempo real.

**Architecture:** Paquete nuevo `org.gipsybuho.recetasfamiliares.dm`, paralelo a `chat/` y `presence/`, sin modificar código estable de `chat/`. Se extiende `ChatStompAuthChannelInterceptor` (único interceptor registrado para todo `/ws`) y `UploadController` (único punto de servido de `/uploads/**`) porque Spring solo permite un interceptor por canal y un controlador por ruta — no se puede evitar tocar estos dos ficheros existentes, pero el cambio es aditivo (nuevas ramas, nada eliminado).

**Tech Stack:** Spring Boot 3.5.15, Java 21, Spring Data JPA, PostgreSQL (Flyway), Spring WebSocket/STOMP (`SimpMessagingTemplate`), JUnit 5 + Mockito + MockMvc, PostgreSQL real de test (`recetas_familiares_test`, vía WireGuard, sin H2/Testcontainers).

## Global Constraints

- Alcance: **paridad completa** con el chat familiar (texto, imágenes, editar/borrar propio, rate limit, exportar, limpiar vista). Sin recibos de lectura visibles. Sin borrar/archivar conversación completa.
- Alcance social: solo entre miembros de una familia compartida (`FamilyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse`).
- El ping de `/topic/users/{userId}/inbox` **nunca** lleva el cuerpo del mensaje, solo `conversationId` + remitente + timestamp.
- Autorización nueva "solo mi propio userId" en el topic de inbox — sin precedente en el proyecto, requiere test explícito de aislamiento.
- Operaciones sobre mensajes de una conversación devuelven **404** (no 403) si el usuario no es participante, para no revelar existencia de conversaciones ajenas. Los checks de membership de familia siguen devolviendo **403**, igual que en todo el proyecto.
- Servir imágenes de chat privado requiere autorización **por participante de conversación**, nunca por membership de familia — family membership filtraría fotos privadas a toda la familia.
- `MAX_BODY_LENGTH = 2000`, `MAX_IMAGE_ATTACHMENTS = 5`, `EDIT_WINDOW = 15 minutos`, mismos valores que el chat familiar (`ChatService`).
- Reutilizar el bean `ChatSendRateLimiter` tal cual (ya es global por `userId`, no por familia) — no crear una clase nueva de rate limiting.
- Nunca exponer entidades JPA directamente; todo por DTOs (`record`).
- Fechas en UTC (`Instant`), JSON camelCase (Jackson por defecto en el proyecto).
- Toda entidad sincronizable incluye `id`, `createdAt`, `updatedAt`, `syncVersion`, `deleted` (regla de `CLAUDE.md`), aunque `deleted` no se active en este sprint para `PrivateConversationEntity` (sin feature de borrar conversación).

---

### Task 1: Migración de esquema (`private_conversations`, `private_messages`, `private_message_attachments`, `private_message_clears`)

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__create_private_chat_schema.sql`

**Interfaces:**
- Produces: 4 tablas nuevas que las Tasks 2-9 mapean vía JPA. Nombres de columna exactos abajo — las entidades de la Task 2 deben coincidir literalmente.

- [ ] **Step 1: Escribir la migración**

```sql
-- Conversacion privada 1:1 entre dos miembros de una misma familia. El par de
-- usuarios va normalizado (user_a_id < user_b_id lexicograficamente) para que
-- exista como maximo una conversacion por par y familia.
CREATE TABLE private_conversations (
    id VARCHAR(36) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    user_a_id VARCHAR(36) NOT NULL,
    user_b_id VARCHAR(36) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_private_conversations_pair UNIQUE (family_id, user_a_id, user_b_id),
    CONSTRAINT fk_private_conversations_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT fk_private_conversations_user_a FOREIGN KEY (user_a_id) REFERENCES users (id),
    CONSTRAINT fk_private_conversations_user_b FOREIGN KEY (user_b_id) REFERENCES users (id)
);

CREATE INDEX ix_private_conversations_user_a ON private_conversations (user_a_id);
CREATE INDEX ix_private_conversations_user_b ON private_conversations (user_b_id);

CREATE TABLE private_messages (
    id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    author_user_id VARCHAR(36) NOT NULL,
    body VARCHAR(2000) NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_private_messages_conversation FOREIGN KEY (conversation_id) REFERENCES private_conversations (id),
    CONSTRAINT fk_private_messages_author FOREIGN KEY (author_user_id) REFERENCES users (id)
);

CREATE INDEX ix_private_messages_conversation_cursor ON private_messages (conversation_id, created_at, id);
CREATE INDEX ix_private_messages_author ON private_messages (author_user_id);

CREATE TABLE private_message_attachments (
    id VARCHAR(36) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    thumbnail_url VARCHAR(1024) NULL,
    storage_path VARCHAR(512) NOT NULL,
    thumbnail_storage_path VARCHAR(512) NULL,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INT NULL,
    height INT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_private_message_attachments_message FOREIGN KEY (message_id) REFERENCES private_messages (id)
);

-- Borrado/limpieza por usuario, igual que chat_message_clears pero por
-- conversacion en vez de por familia: cada participante puede ocultar su
-- propia vista del historial sin afectar al otro.
CREATE TABLE private_message_clears (
    id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    cleared_before timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uq_private_message_clears_conversation_user UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_private_message_clears_conversation FOREIGN KEY (conversation_id) REFERENCES private_conversations (id),
    CONSTRAINT fk_private_message_clears_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

- [ ] **Step 2: Verificar que la migración aplica limpiamente**

Cargar credenciales de test y arrancar el contexto Spring una vez (Flyway aplica migraciones al arrancar):

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=BackendApplicationTests test
```

Expected: `BUILD SUCCESS`. Si Flyway falla (columna/tabla mal escrita), el arranque del contexto falla con `FlywayException` y el log señala la línea del SQL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V19__create_private_chat_schema.sql
git commit -m "feat(backend): esquema de chat privado 1:1 (V19)"
```

---

### Task 2: Entidades y repositorios `dm/`

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateConversationEntity.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateConversationRepository.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageEntity.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageRepository.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageAttachmentEntity.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageAttachmentRepository.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageClearEntity.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageClearRepository.java`

**Interfaces:**
- Consumes: tablas de la Task 1 (nombres de columna exactos); `FamilyEntity`/`UserEntity` de `org.gipsybuho.recetasfamiliares.families`/`users` (ya existentes, mismos getters `getId()` usados en `ChatMessageEntity`).
- Produces: `PrivateConversationEntity.getId()/getFamilyId()/getUserAId()/getUserBId()/hasParticipant(String)/otherParticipant(String)`; `PrivateMessageEntity.getId()/getConversationId()/getAuthorUserId()/getAuthorDisplayName()/getBody()/getAttachments()/addAttachment(...)/editBody(String)/softDelete()`; `PrivateMessageAttachmentEntity` mismos getters que `ChatAttachmentEntity`; `PrivateMessageClearEntity.getClearedBefore()/setClearedBefore(Instant)`. Repositorios consumidos por la Task 5-7 (`PrivateChatService`) y Task 8 (interceptor).

Sin test dedicado en este task: el proyecto no tiene tests aislados para `ChatMessageEntity`/`ChatAttachmentEntity`/`ChatMessageClearEntity` ni sus repositorios (verificado: `backend/src/test/java/.../chat/` solo contiene `ChatControllerTest` y `ChatStompAuthChannelInterceptorTest`). Estas entidades se verifican indirectamente en la Task 5 vía `PrivateChatControllerTest` (integración real contra Postgres de test). Este task solo debe compilar.

- [ ] **Step 1: `PrivateConversationEntity.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.users.UserEntity;

/**
 * Conversacion privada 1:1 entre dos miembros de una misma familia. El par de
 * usuarios va normalizado (userA.id < userB.id lexicograficamente) para que
 * exista como maximo una conversacion por par y familia, sin importar quien
 * la inicio.
 */
@Entity
@Table(name = "private_conversations")
public class PrivateConversationEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "varchar(36)")
    private FamilyEntity family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity userB;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(nullable = false)
    private boolean deleted;

    protected PrivateConversationEntity() {
    }

    public PrivateConversationEntity(FamilyEntity family, UserEntity userA, UserEntity userB) {
        this.family = family;
        this.userA = userA;
        this.userB = userB;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getFamilyId() {
        return family.getId();
    }

    public String getUserAId() {
        return userA.getId();
    }

    public String getUserBId() {
        return userB.getId();
    }

    /** true si userId es uno de los dos participantes de esta conversacion. */
    public boolean hasParticipant(String userId) {
        return getUserAId().equals(userId) || getUserBId().equals(userId);
    }

    /** El otro participante distinto de userId. Llamar solo si hasParticipant(userId) es true. */
    public String otherParticipant(String userId) {
        return getUserAId().equals(userId) ? getUserBId() : getUserAId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getSyncVersion() {
        return syncVersion;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
```

- [ ] **Step 2: `PrivateConversationRepository.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateConversationRepository extends JpaRepository<PrivateConversationEntity, String> {

    Optional<PrivateConversationEntity> findByFamily_IdAndUserA_IdAndUserB_Id(
            String familyId, String userAId, String userBId);

    Optional<PrivateConversationEntity> findByIdAndDeletedFalse(String id);

    @Query("""
            SELECT c FROM PrivateConversationEntity c
            WHERE c.family.id = :familyId
              AND (c.userA.id = :userId OR c.userB.id = :userId)
              AND c.deleted = false
            """)
    List<PrivateConversationEntity> findAllForParticipant(
            @Param("familyId") String familyId, @Param("userId") String userId);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM PrivateConversationEntity c
            WHERE c.id = :conversationId
              AND (c.userA.id = :userId OR c.userB.id = :userId)
            """)
    boolean existsByIdAndParticipant(@Param("conversationId") String conversationId, @Param("userId") String userId);
}
```

- [ ] **Step 3: `PrivateMessageEntity.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

import org.gipsybuho.recetasfamiliares.users.UserEntity;

/**
 * Mensaje de texto del chat privado 1:1. Mismo shape que ChatMessageEntity
 * (chat familiar) pero el scope es la conversacion, no la familia.
 */
@Entity
@Table(name = "private_messages")
public class PrivateMessageEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, columnDefinition = "varchar(36)")
    private PrivateConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity author;

    @Column(length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC, id ASC")
    @BatchSize(size = 50)
    private List<PrivateMessageAttachmentEntity> attachments = new ArrayList<>();

    protected PrivateMessageEntity() {
    }

    public PrivateMessageEntity(String id, PrivateConversationEntity conversation, UserEntity author, String body) {
        this.id = id;
        this.conversation = conversation;
        this.author = author;
        this.body = body;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversation.getId();
    }

    public String getAuthorUserId() {
        return author.getId();
    }

    public String getAuthorDisplayName() {
        return author.getDisplayName();
    }

    public String getBody() {
        return deleted ? null : body;
    }

    public List<PrivateMessageAttachmentEntity> getAttachments() {
        if (deleted) {
            return List.of();
        }
        return attachments.stream()
                .filter(attachment -> !attachment.isDeleted())
                .toList();
    }

    public void addAttachment(PrivateMessageAttachmentEntity attachment) {
        attachments.add(attachment);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getSyncVersion() {
        return syncVersion;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void editBody(String body) {
        if (!deleted) {
            this.body = body;
            updatedAt = Instant.now();
            syncVersion++;
        }
    }

    public void softDelete() {
        if (!deleted) {
            deleted = true;
            body = null;
            attachments.forEach(PrivateMessageAttachmentEntity::softDelete);
            deletedAt = Instant.now();
            updatedAt = Instant.now();
            syncVersion++;
        }
    }
}
```

- [ ] **Step 4: `PrivateMessageRepository.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessageEntity, String> {

    Optional<PrivateMessageEntity> findByIdAndConversation_Id(String id, String conversationId);

    Optional<PrivateMessageEntity> findByIdAndConversation_IdAndDeletedFalse(String id, String conversationId);

    Optional<PrivateMessageEntity> findFirstByConversation_IdOrderByCreatedAtDescIdDesc(String conversationId);

    @Query("""
            SELECT m FROM PrivateMessageEntity m
            WHERE m.conversation.id = :conversationId
              AND m.createdAt > :clearedBefore
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<PrivateMessageEntity> findHistory(
            @Param("conversationId") String conversationId,
            @Param("clearedBefore") Instant clearedBefore,
            Pageable pageable
    );

    @Query("""
            SELECT m FROM PrivateMessageEntity m
            WHERE m.conversation.id = :conversationId
              AND m.createdAt > :clearedBefore
              AND (
                    m.createdAt < :beforeCreatedAt
                    OR (m.createdAt = :beforeCreatedAt AND m.id < :beforeId)
              )
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<PrivateMessageEntity> findHistoryBefore(
            @Param("conversationId") String conversationId,
            @Param("clearedBefore") Instant clearedBefore,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeId") String beforeId,
            Pageable pageable
    );

    @Query("""
            SELECT m FROM PrivateMessageEntity m
            WHERE m.conversation.id = :conversationId
              AND m.createdAt > :clearedBefore
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<PrivateMessageEntity> findVisibleForExport(
            @Param("conversationId") String conversationId,
            @Param("clearedBefore") Instant clearedBefore
    );
}
```

- [ ] **Step 5: `PrivateMessageAttachmentEntity.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "private_message_attachments")
public class PrivateMessageAttachmentEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, columnDefinition = "varchar(36)")
    private PrivateMessageEntity message;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "thumbnail_storage_path", length = 512)
    private String thumbnailStoragePath;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    private Integer width;

    private Integer height;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected PrivateMessageAttachmentEntity() {
    }

    public PrivateMessageAttachmentEntity(
            PrivateMessageEntity message,
            String url,
            String thumbnailUrl,
            String storagePath,
            String thumbnailStoragePath,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height
    ) {
        this.message = message;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.storagePath = storagePath;
        this.thumbnailStoragePath = thumbnailStoragePath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getThumbnailStoragePath() {
        return thumbnailStoragePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void softDelete() {
        if (!deleted) {
            deleted = true;
            deletedAt = Instant.now();
            updatedAt = deletedAt;
        }
    }
}
```

- [ ] **Step 6: `PrivateMessageAttachmentRepository.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateMessageAttachmentRepository extends JpaRepository<PrivateMessageAttachmentEntity, String> {

    @Query("""
            SELECT a.message.conversation.id
            FROM PrivateMessageAttachmentEntity a
            WHERE a.deleted = false
              AND (a.storagePath = :storagePath OR a.thumbnailStoragePath = :storagePath)
            """)
    List<String> findOwningConversationIdsByStoragePath(@Param("storagePath") String storagePath);
}
```

- [ ] **Step 7: `PrivateMessageClearEntity.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.gipsybuho.recetasfamiliares.users.UserEntity;

/**
 * Marca de limpieza del chat privado por usuario, igual que
 * ChatMessageClearEntity pero por conversacion en vez de por familia.
 */
@Entity
@Table(name = "private_message_clears")
public class PrivateMessageClearEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, columnDefinition = "varchar(36)")
    private PrivateConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity user;

    @Column(name = "cleared_before", nullable = false)
    private Instant clearedBefore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PrivateMessageClearEntity() {
    }

    public PrivateMessageClearEntity(PrivateConversationEntity conversation, UserEntity user, Instant clearedBefore) {
        this.conversation = conversation;
        this.user = user;
        this.clearedBefore = clearedBefore;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Instant getClearedBefore() {
        return clearedBefore;
    }

    public void setClearedBefore(Instant clearedBefore) {
        this.clearedBefore = clearedBefore;
    }
}
```

- [ ] **Step 8: `PrivateMessageClearRepository.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivateMessageClearRepository extends JpaRepository<PrivateMessageClearEntity, String> {

    Optional<PrivateMessageClearEntity> findByConversation_IdAndUser_Id(String conversationId, String userId);
}
```

- [ ] **Step 9: Compilar**

```bash
cd backend
mvn -q -DskipTests compile
```

Expected: `BUILD SUCCESS`, sin errores de compilación.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/
git commit -m "feat(backend): entidades y repositorios de chat privado (dm/)"
```

---

### Task 3: DTOs `dm/`

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageAttachmentResponse.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageResponse.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateConversationResponse.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageHistoryResponse.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateMessageExportResponse.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/SendPrivateMessageRequest.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/EditPrivateMessageRequest.java`

**Interfaces:**
- Consumes: `PrivateMessageEntity`, `PrivateMessageAttachmentEntity`, `PrivateConversationEntity` de la Task 2.
- Produces: `PrivateMessageResponse.from(PrivateMessageEntity)`, `PrivateMessageAttachmentResponse.from(PrivateMessageAttachmentEntity)` — usados por la Task 4 (publisher) y Tasks 5-7 (service).

Sin test dedicado (mismo criterio que `ChatMessageResponse`/`ChatAttachmentResponse`/`ChatHistoryResponse`/`ChatExportResponse`, que tampoco lo tienen — se verifican vía `jsonPath` en `PrivateChatControllerTest`, Tasks 5-7).

- [ ] **Step 1: `PrivateMessageAttachmentResponse.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

public record PrivateMessageAttachmentResponse(
        String id,
        String url,
        String thumbnailUrl,
        String contentType,
        long sizeBytes,
        Integer width,
        Integer height
) {
    static PrivateMessageAttachmentResponse from(PrivateMessageAttachmentEntity attachment) {
        return new PrivateMessageAttachmentResponse(
                attachment.getId(),
                attachment.getUrl(),
                attachment.getThumbnailUrl(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getWidth(),
                attachment.getHeight()
        );
    }
}
```

- [ ] **Step 2: `PrivateMessageResponse.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.List;

/**
 * DTO de mensaje de chat privado. {@code body} es null cuando el mensaje esta
 * borrado. Entidad sincronizable estandar: id, createdAt, updatedAt,
 * syncVersion, deleted.
 */
public record PrivateMessageResponse(
        String id,
        String conversationId,
        String authorUserId,
        String authorDisplayName,
        String body,
        List<PrivateMessageAttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
    static PrivateMessageResponse from(PrivateMessageEntity message) {
        return new PrivateMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getAuthorUserId(),
                message.getAuthorDisplayName(),
                message.getBody(),
                message.getAttachments().stream().map(PrivateMessageAttachmentResponse::from).toList(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.getSyncVersion(),
                message.isDeleted()
        );
    }
}
```

- [ ] **Step 3: `PrivateConversationResponse.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;

/**
 * Fila de la bandeja de conversaciones: la conversacion vista desde la
 * perspectiva del usuario que la solicita (otherUser* siempre es el otro
 * participante, nunca el propio usuario autenticado).
 */
public record PrivateConversationResponse(
        String conversationId,
        String otherUserId,
        String otherUserDisplayName,
        String otherUserAvatarUrl,
        String lastMessagePreview,
        Instant lastMessageAt
) {
}
```

- [ ] **Step 4: `PrivateMessageHistoryResponse.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.util.List;

/**
 * Pagina de historial por cursor. {@code items} viene descendente (mas
 * reciente primero). {@code nextBefore} es el id a pasar como {@code before}
 * para cargar la pagina anterior; null si no hay mas.
 */
public record PrivateMessageHistoryResponse(
        List<PrivateMessageResponse> items,
        boolean hasMore,
        String nextBefore
) {
}
```

- [ ] **Step 5: `PrivateMessageExportResponse.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.List;

public record PrivateMessageExportResponse(
        String conversationId,
        Instant exportedAt,
        int totalMessages,
        List<PrivateMessageResponse> messages
) {
}
```

- [ ] **Step 6: `SendPrivateMessageRequest.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Envio de mensaje de texto. El cliente puede generar el {@code id} (UUID v4)
 * para idempotencia: reenviar el mismo id no duplica.
 */
public record SendPrivateMessageRequest(
        @Size(max = 36)
        String id,

        @NotBlank
        @Size(max = 2000)
        String body
) {
}
```

- [ ] **Step 7: `EditPrivateMessageRequest.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditPrivateMessageRequest(
        @NotBlank
        @Size(max = 2000)
        String body
) {
}
```

- [ ] **Step 8: Compilar y commit**

```bash
cd backend
mvn -q -DskipTests compile
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/*.java
git commit -m "feat(backend): DTOs de chat privado (dm/)"
```

---

### Task 4: `PrivateConversationRealtimePublisher` (topics de conversación + inbox)

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateInboxPing.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateConversationRealtimePublisher.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/dm/PrivateConversationRealtimePublisherTest.java`

**Interfaces:**
- Consumes: `PrivateMessageResponse` (Task 3), `SimpMessagingTemplate` (Spring, ya usado por `ChatRealtimePublisher`/`PresencePublisher`).
- Produces: `PrivateConversationRealtimePublisher.publish(PrivateMessageResponse message, String recipientUserId)`, usado por las Tasks 6-7. `conversationTopicFor`/`inboxTopicFor` quedan package-private (mismo criterio que `ChatRealtimePublisher.topicFor`) y solo sirven a este publisher — el interceptor de la Task 8 vive en el paquete `chat`, no `dm`, así que define sus **propias** constantes de prefijo con el mismo valor literal, exactamente como ya hace hoy `ChatStompAuthChannelInterceptor.TOPIC_PREFIX` frente a `ChatRealtimePublisher.TOPIC_PREFIX` (duplicación mínima ya aceptada en el proyecto, no se comparte entre paquetes).

Este publisher SÍ lleva test dedicado (a diferencia de las entidades/DTOs): mismo criterio que `PresencePublisherTest` (publicador de topic STOMP nuevo, con lógica de decisión — a qué topics publica — que merece verificación aislada).

- [ ] **Step 1: Escribir el test (RED)**

```java
package org.gipsybuho.recetasfamiliares.dm;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PrivateConversationRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private PrivateConversationRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PrivateConversationRealtimePublisher(messagingTemplate);
    }

    @Test
    void publishesMessageToConversationTopic() {
        PrivateMessageResponse message = new PrivateMessageResponse(
                "msg-1", "conv-1", "author-1", "Author", "hola",
                List.of(), Instant.EPOCH, Instant.EPOCH, 0L, false);

        publisher.publish(message, "recipient-1");

        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv-1"), eq(message));
    }

    @Test
    void publishesInboxPingToRecipientOnlyWithoutMessageBody() {
        PrivateMessageResponse message = new PrivateMessageResponse(
                "msg-2", "conv-2", "author-2", "Author", "contenido secreto",
                List.of(), Instant.EPOCH, Instant.EPOCH, 0L, false);

        publisher.publish(message, "recipient-2");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/users/recipient-2/inbox"),
                eq(new PrivateInboxPing("conv-2", "author-2", Instant.EPOCH)));
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
cd backend
mvn -q -Dtest=PrivateConversationRealtimePublisherTest test
```

Expected: FAIL — `PrivateConversationRealtimePublisher`/`PrivateInboxPing` no existen todavía.

- [ ] **Step 3: `PrivateInboxPing.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;

/**
 * Ping ligero del topic de bandeja ({@code /topic/users/{userId}/inbox}).
 * Nunca lleva el cuerpo del mensaje (decision de seguridad del spec): solo
 * suficiente informacion para que el cliente refresque su bandeja/badge y
 * decida si pide el contenido real por REST o por el topic de la conversacion.
 */
public record PrivateInboxPing(
        String conversationId,
        String senderUserId,
        Instant sentAt
) {
}
```

- [ ] **Step 4: `PrivateConversationRealtimePublisher.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica mensajes privados nuevos por WebSocket en dos topics distintos:
 * el de la conversacion (contenido completo, para quien la tiene abierta) y
 * el de bandeja del destinatario (solo metadata, sin cuerpo del mensaje, para
 * badge/refresco de la lista de conversaciones sin tener que suscribirse a
 * cada conversacion individualmente).
 */
@Component
public class PrivateConversationRealtimePublisher {

    static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";
    static final String INBOX_TOPIC_PREFIX = "/topic/users/";
    static final String INBOX_TOPIC_SUFFIX = "/inbox";

    private final SimpMessagingTemplate messagingTemplate;

    public PrivateConversationRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    static String conversationTopicFor(String conversationId) {
        return CONVERSATION_TOPIC_PREFIX + conversationId;
    }

    static String inboxTopicFor(String userId) {
        return INBOX_TOPIC_PREFIX + userId + INBOX_TOPIC_SUFFIX;
    }

    void publish(PrivateMessageResponse message, String recipientUserId) {
        messagingTemplate.convertAndSend(conversationTopicFor(message.conversationId()), message);
        messagingTemplate.convertAndSend(
                inboxTopicFor(recipientUserId),
                new PrivateInboxPing(message.conversationId(), message.authorUserId(), message.updatedAt()));
    }
}
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

```bash
cd backend
mvn -q -Dtest=PrivateConversationRealtimePublisherTest test
```

Expected: PASS, 2/2.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateInboxPing.java \
        backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateConversationRealtimePublisher.java \
        backend/src/test/java/org/gipsybuho/recetasfamiliares/dm/PrivateConversationRealtimePublisherTest.java
git commit -m "feat(backend): publica mensajes privados a topic de conversacion + inbox"
```

---

### Task 5: `PrivateChatService` + `PrivateChatController` — crear/listar conversaciones

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatService.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatController.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatControllerTest.java`

**Interfaces:**
- Consumes: todo lo de Tasks 2-4 (`PrivateConversationRepository`, `PrivateMessageRepository`, `PrivateMessageClearRepository`, DTOs, `PrivateConversationRealtimePublisher`), más `FamilyRepository`, `FamilyMemberRepository`, `UserRepository` (ya existentes en `org.gipsybuho.recetasfamiliares.families`/`users`), `ChatSendRateLimiter` y `FileStorageService` (ya existentes en `org.gipsybuho.recetasfamiliares.chat`/`photos`, **reutilizados tal cual**, sin clases nuevas).
- Produces: `PrivateChatService.createOrGetConversation(String familyId, String requesterId, String otherUserId)`, `PrivateChatService.listConversations(String familyId, String userId)` — el resto de métodos del servicio (`listHistory`, `sendMessage`, `sendImageMessage`, `editMessage`, `deleteMessage`, `clearForUser`, `exportForUser`) se añaden en las Tasks 6-7 al MISMO fichero. `PrivateChatController` mapeado en `/api/v1/families/{familyId}/conversations`.

Este es el primer task con TDD real de extremo a extremo (mismo patrón que `ChatControllerTest`: `@SpringBootTest` + `MockMvc` contra la base de test real, sin mocks). `PrivateChatService` no lleva test propio (mismo criterio que `ChatService`, que tampoco lo tiene) — se verifica aquí, vía HTTP.

- [ ] **Step 1: Escribir el test (RED)**

```java
package org.gipsybuho.recetasfamiliares.dm;

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
class PrivateChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsConversationBetweenTwoFamilyMembers() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-create-owner"), "Familia DM Create");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-create-guest"));

        MvcResult result = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.otherUserId").value(guest.userId()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String conversationId = body.get("conversationId").asText();

        // Idempotente: pedirla de nuevo devuelve el mismo id, sin importar quien la pide
        // (guest pide la conversacion con owner, direccion inversa a la primera peticion).
        mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), owner.userId())
                        .header("Authorization", "Bearer " + guest.accessToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId));
    }

    @Test
    void rejectsConversationWithNonFamilyMember() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-outsider-owner"), "Familia DM Outsider");
        RegisteredUser outsider = register(uniqueEmail("dm-outsider-other"), "Familia DM Outsider Otra");

        mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), outsider.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsConversationWithSelf() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-self-owner"), "Familia DM Self");

        mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), owner.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listConversationsReturnsOnlyConversationsOfRequester() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-list-owner"), "Familia DM List");
        RegisteredUser guestA = invite(owner, uniqueEmail("dm-list-guest-a"));
        RegisteredUser guestB = invite(owner, uniqueEmail("dm-list-guest-b"));

        mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), guestA.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        // guestB no tiene conversacion con nadie: su bandeja debe estar vacia.
        mockMvc.perform(get("/api/v1/families/{familyId}/conversations", owner.familyId())
                        .header("Authorization", "Bearer " + guestB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].otherUserId").value(guestA.userId()));
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "DM User",
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
                response.get("user").get("id").asText());
    }

    /** Registra un segundo usuario y lo invita a la familia de {@code owner}. */
    private RegisteredUser invite(RegisteredUser owner, String guestEmail) throws Exception {
        RegisteredUser guest = register(guestEmail, "Familia DM Guest " + guestEmail);
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());
        return guest;
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    /** Mismo patron que ChatControllerTest.validJpeg(): imagen real decodificable,
     * no bytes con solo cabecera magica (FileStorageService.storeWithThumbnail
     * decodifica de verdad para generar el thumbnail y width/height). */
    private byte[] validJpeg() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(image, "jpg", out)) {
            throw new IllegalStateException("No JPEG writer available");
        }
        return out.toByteArray();
    }

    private record RegisteredUser(String accessToken, String familyId, String userId) {}
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=PrivateChatControllerTest test
```

Expected: FAIL en compilación — `PrivateChatController`/`PrivateChatService` no existen todavía.

- [ ] **Step 3: `PrivateChatService.java` (createOrGetConversation + listConversations)**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.util.Comparator;
import java.util.List;

import org.gipsybuho.recetasfamiliares.chat.ChatSendRateLimiter;
import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.photos.FileStorageService;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PrivateChatService {

    private final PrivateConversationRepository conversationRepository;
    private final PrivateMessageRepository messageRepository;
    private final PrivateMessageClearRepository clearRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final ChatSendRateLimiter rateLimiter;
    private final PrivateConversationRealtimePublisher realtimePublisher;
    private final FileStorageService fileStorageService;

    public PrivateChatService(
            PrivateConversationRepository conversationRepository,
            PrivateMessageRepository messageRepository,
            PrivateMessageClearRepository clearRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            UserRepository userRepository,
            ChatSendRateLimiter rateLimiter,
            PrivateConversationRealtimePublisher realtimePublisher,
            FileStorageService fileStorageService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.clearRepository = clearRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.realtimePublisher = realtimePublisher;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public PrivateConversationResponse createOrGetConversation(String familyId, String requesterId, String otherUserId) {
        requireMembership(familyId, requesterId);
        if (otherUserId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot start a conversation with yourself");
        }
        requireMembership(familyId, otherUserId);

        String userAId = requesterId.compareTo(otherUserId) < 0 ? requesterId : otherUserId;
        String userBId = requesterId.compareTo(otherUserId) < 0 ? otherUserId : requesterId;

        PrivateConversationEntity existing = conversationRepository
                .findByFamily_IdAndUserA_IdAndUserB_Id(familyId, userAId, userBId)
                .orElse(null);
        if (existing != null) {
            return toConversationResponse(existing, requesterId);
        }

        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        UserEntity userA = userRepository.findByIdAndDeletedFalse(userAId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        UserEntity userB = userRepository.findByIdAndDeletedFalse(userBId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        PrivateConversationEntity saved = conversationRepository.save(
                new PrivateConversationEntity(family, userA, userB));
        return toConversationResponse(saved, requesterId);
    }

    @Transactional(readOnly = true)
    public List<PrivateConversationResponse> listConversations(String familyId, String userId) {
        requireMembership(familyId, userId);
        return conversationRepository.findAllForParticipant(familyId, userId).stream()
                .map(conversation -> toConversationResponse(conversation, userId))
                .sorted(Comparator.comparing(
                        PrivateConversationResponse::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private PrivateConversationResponse toConversationResponse(PrivateConversationEntity conversation, String requesterId) {
        String otherUserId = conversation.otherParticipant(requesterId);
        UserEntity otherUser = userRepository.findByIdAndDeletedFalse(otherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        PrivateMessageEntity lastMessage = messageRepository
                .findFirstByConversation_IdOrderByCreatedAtDescIdDesc(conversation.getId())
                .orElse(null);
        return new PrivateConversationResponse(
                conversation.getId(),
                otherUserId,
                otherUser.getDisplayName(),
                otherUser.getAvatarUrl(),
                lastMessage == null ? null : lastMessage.getBody(),
                lastMessage == null ? null : lastMessage.getCreatedAt()
        );
    }

    private void requireMembership(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
    }
}
```

- [ ] **Step 4: `PrivateChatController.java`**

```java
package org.gipsybuho.recetasfamiliares.dm;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/families/{familyId}/conversations")
public class PrivateChatController {

    private final PrivateChatService chatService;

    public PrivateChatController(PrivateChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/with/{otherUserId}")
    public PrivateConversationResponse createOrGetConversation(
            @PathVariable String familyId,
            @PathVariable String otherUserId,
            Authentication authentication
    ) {
        return chatService.createOrGetConversation(familyId, authentication.getName(), otherUserId);
    }

    @GetMapping
    public List<PrivateConversationResponse> listConversations(
            @PathVariable String familyId,
            Authentication authentication
    ) {
        return chatService.listConversations(familyId, authentication.getName());
    }
}
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=PrivateChatControllerTest test
```

Expected: PASS, 4/4.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatService.java \
        backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatController.java \
        backend/src/test/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatControllerTest.java
git commit -m "feat(backend): crear/listar conversaciones privadas"
```

---

### Task 6: `PrivateChatService`/`PrivateChatController` — enviar texto, imágenes e historial

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatService.java`
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatController.java`
- Modify: `backend/src/test/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatControllerTest.java`

**Interfaces:**
- Consumes: `PrivateChatService`/`PrivateChatController` de la Task 5 (mismo fichero, se amplía); `FileStorageService.storeWithThumbnail(MultipartFile, String, String)` y `FileStorageService.deleteStoredPath(String)` (ya usados por `ChatService`, firma idéntica); `MAX_IMAGE_ATTACHMENTS`/`MAX_BODY_LENGTH` mismos valores que `ChatService`.
- Produces: `PrivateChatService.listHistory(String conversationId, String userId, String before, Integer limit)`, `.sendMessage(String conversationId, String userId, SendPrivateMessageRequest request)`, `.sendImageMessage(String conversationId, String userId, String id, String body, List<MultipartFile> files)` — usados por Task 7 (comparten el helper `requireParticipantConversation`).

- [ ] **Step 1: Añadir tests (RED)**

Añadir estos métodos a `PrivateChatControllerTest` (dentro de la clase, antes de los helpers privados):

```java
    @Test
    void sendsTextMessageAndListsHistory() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-send-owner"), "Familia DM Send");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-send-guest"));
        String conversationId = createConversation(owner, guest.userId());

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "hola guest"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("hola guest"))
                .andExpect(jsonPath("$.authorUserId").value(owner.userId()));

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("hola guest"));
    }

    @Test
    void blocksMessageAccessForNonParticipant() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-block-owner"), "Familia DM Block");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-block-guest"));
        RegisteredUser outsider = invite(owner, uniqueEmail("dm-block-outsider"));
        String conversationId = createConversation(owner, guest.userId());

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + outsider.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "no deberia poder"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendingTextMessageIsIdempotentByClientId() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-idem-owner"), "Familia DM Idem");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-idem-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String clientId = java.util.UUID.randomUUID().toString();

        String payload = """
                {"id": "%s", "body": "reintento"}
                """.formatted(clientId);

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void sendsImageMessageAndServesAttachmentOnlyToParticipants() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-image-owner"), "Familia DM Image");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-image-guest"));
        RegisteredUser outsider = invite(owner, uniqueEmail("dm-image-outsider"));
        String conversationId = createConversation(owner, guest.userId());

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", validJpeg());

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/families/{familyId}/conversations/{conversationId}/messages/images",
                                owner.familyId(), conversationId)
                        .file(file)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String attachmentUrl = body.get("attachments").get(0).get("url").asText();
        String path = attachmentUrl.substring(attachmentUrl.indexOf("/uploads/"));

        mockMvc.perform(get(path).header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rateLimitsBurstSends() throws Exception {
        // ChatSendRateLimiter es el mismo bean que usa el chat familiar (compartido,
        // por userId — ver Global Constraints), ya probado en ChatControllerTest;
        // este test solo verifica el cableado en PrivateChatService: cuando el
        // limiter deniega, el endpoint responde 429 en vez de crear el mensaje.
        RegisteredUser owner = register(uniqueEmail("dm-ratelimit-owner"), "Familia DM RateLimit");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-ratelimit-guest"));
        String conversationId = createConversation(owner, guest.userId());

        int tooMany = 11; // limite por defecto: app.security.rate-limit.chat.max-messages=10
        int lastStatus = 0;
        for (int i = 0; i < tooMany; i++) {
            lastStatus = mockMvc.perform(post(
                            "/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                            owner.familyId(), conversationId)
                            .header("Authorization", "Bearer " + owner.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"body": "burst %d"}
                                    """.formatted(i)))
                    .andReturn().getResponse().getStatus();
        }

        org.junit.jupiter.api.Assertions.assertEquals(429, lastStatus);
    }

    private String createConversation(RegisteredUser owner, String otherUserId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), otherUserId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.get("conversationId").asText();
    }
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=PrivateChatControllerTest test
```

Expected: FAIL — `listHistory`/`sendMessage`/`sendImageMessage` no existen en `PrivateChatService`/`PrivateChatController` todavía.

- [ ] **Step 3: Añadir a `PrivateChatService.java`**

Añadir estos imports al principio del fichero (junto a los ya existentes de la Task 5):

```java
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
```

(`PrivateMessageAttachmentEntity` no necesita import: está en el mismo paquete `dm` que `PrivateChatService`.)

Añadir estas constantes junto a la clase (mismos valores que `ChatService`):

```java
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_BODY_LENGTH = 2000;
    private static final int MAX_IMAGE_ATTACHMENTS = 5;
```

Añadir estos métodos a la clase (después de `listConversations`, antes de `toConversationResponse`):

```java
    @Transactional(readOnly = true)
    public PrivateMessageHistoryResponse listHistory(String conversationId, String userId, String before, Integer limit) {
        PrivateConversationEntity conversation = requireParticipantConversation(conversationId, userId);
        int pageSize = normalizeLimit(limit);
        Instant clearedBefore = clearedBefore(conversation.getId(), userId);

        Instant beforeCreatedAt = null;
        String beforeId = null;
        if (before != null && !before.isBlank()) {
            PrivateMessageEntity cursor = messageRepository
                    .findByIdAndConversation_Id(before, conversation.getId()).orElse(null);
            if (cursor != null) {
                beforeCreatedAt = cursor.getCreatedAt();
                beforeId = cursor.getId();
            }
        }

        Pageable pageable = PageRequest.of(0, pageSize + 1);
        List<PrivateMessageEntity> rows = beforeCreatedAt == null
                ? messageRepository.findHistory(conversation.getId(), clearedBefore, pageable)
                : messageRepository.findHistoryBefore(conversation.getId(), clearedBefore, beforeCreatedAt, beforeId, pageable);

        boolean hasMore = rows.size() > pageSize;
        List<PrivateMessageEntity> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<PrivateMessageResponse> items = pageRows.stream().map(PrivateMessageResponse::from).toList();
        String nextBefore = hasMore && !items.isEmpty() ? items.get(items.size() - 1).id() : null;
        return new PrivateMessageHistoryResponse(items, hasMore, nextBefore);
    }

    @Transactional
    public PrivateMessageResponse sendMessage(String conversationId, String userId, SendPrivateMessageRequest request) {
        PrivateConversationEntity conversation = requireParticipantConversation(conversationId, userId);

        String clientId = request.id() == null || request.id().isBlank() ? null : request.id().trim();
        if (clientId != null) {
            PrivateMessageEntity existing = messageRepository.findById(clientId).orElse(null);
            if (existing != null) {
                if (existing.getConversationId().equals(conversation.getId()) && existing.getAuthorUserId().equals(userId)) {
                    return PrivateMessageResponse.from(existing);
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Message id already used");
            }
        }

        if (!rateLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages, slow down");
        }

        UserEntity author = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        PrivateMessageEntity message = new PrivateMessageEntity(clientId, conversation, author, request.body().trim());
        PrivateMessageEntity saved = messageRepository.save(message);
        PrivateMessageResponse response = PrivateMessageResponse.from(saved);
        publishAfterCommit(response, conversation.otherParticipant(userId), null);
        return response;
    }

    @Transactional
    public PrivateMessageResponse sendImageMessage(
            String conversationId,
            String userId,
            String id,
            String body,
            List<MultipartFile> files
    ) {
        PrivateConversationEntity conversation = requireParticipantConversation(conversationId, userId);
        List<MultipartFile> images = normalizeImageFiles(files);
        String clientId = normalizeClientId(id);
        PrivateMessageEntity existing = findExistingMessage(clientId, conversation.getId(), userId);
        if (existing != null) {
            return PrivateMessageResponse.from(existing);
        }

        String normalizedBody = normalizeOptionalBody(body);
        if (!rateLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages, slow down");
        }

        UserEntity author = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        PrivateMessageEntity message = new PrivateMessageEntity(clientId, conversation, author, normalizedBody);
        List<FileStorageService.StoredFile> storedFiles = new ArrayList<>();
        try {
            for (MultipartFile file : images) {
                FileStorageService.StoredFile stored = storeImage(file);
                storedFiles.add(stored);
                message.addAttachment(new PrivateMessageAttachmentEntity(
                        message,
                        stored.url(),
                        stored.thumbnailUrl(),
                        stored.storagePath(),
                        stored.thumbnailStoragePath(),
                        stored.contentType(),
                        stored.sizeBytes(),
                        stored.width(),
                        stored.height()
                ));
            }
        } catch (RuntimeException e) {
            cleanupStoredFiles(storedFiles);
            throw e;
        }

        try {
            PrivateMessageEntity saved = messageRepository.save(message);
            PrivateMessageResponse response = PrivateMessageResponse.from(saved);
            publishAfterCommit(response, conversation.otherParticipant(userId), storedFiles);
            return response;
        } catch (RuntimeException e) {
            cleanupStoredFiles(storedFiles);
            throw e;
        }
    }

    private void publishAfterCommit(
            PrivateMessageResponse response,
            String recipientUserId,
            List<FileStorageService.StoredFile> storedFiles
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            realtimePublisher.publish(response, recipientUserId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                realtimePublisher.publish(response, recipientUserId);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK && storedFiles != null) {
                    cleanupStoredFiles(storedFiles);
                }
            }
        });
    }

    private Instant clearedBefore(String conversationId, String userId) {
        return clearRepository.findByConversation_IdAndUser_Id(conversationId, userId)
                .map(PrivateMessageClearEntity::getClearedBefore)
                .orElse(Instant.EPOCH);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private PrivateConversationEntity requireParticipantConversation(String conversationId, String userId) {
        PrivateConversationEntity conversation = conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.hasParticipant(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        return conversation;
    }

    private List<MultipartFile> normalizeImageFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image is required");
        }
        List<MultipartFile> images = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image is required");
        }
        if (images.size() > MAX_IMAGE_ATTACHMENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 5 images per message");
        }
        return images;
    }

    private String normalizeOptionalBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String text = body.trim();
        if (text.length() > MAX_BODY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message body is too long");
        }
        return text;
    }

    private String normalizeClientId(String id) {
        return id == null || id.isBlank() ? null : id.trim();
    }

    private PrivateMessageEntity findExistingMessage(String clientId, String conversationId, String userId) {
        if (clientId == null) {
            return null;
        }
        PrivateMessageEntity existing = messageRepository.findById(clientId).orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.getConversationId().equals(conversationId) && existing.getAuthorUserId().equals(userId)) {
            return existing;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Message id already used");
    }

    private FileStorageService.StoredFile storeImage(MultipartFile file) {
        try {
            return fileStorageService.storeWithThumbnail(file, "dm", "dm_thumbnails");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded image");
        }
    }

    private void cleanupStoredFiles(List<FileStorageService.StoredFile> storedFiles) {
        for (FileStorageService.StoredFile stored : storedFiles) {
            fileStorageService.deleteStoredPath(stored.storagePath());
            fileStorageService.deleteStoredPath(stored.thumbnailStoragePath());
        }
    }
```

- [ ] **Step 4: Añadir a `PrivateChatController.java`**

Añadir estos imports (`java.util.List` NO se repite: ya lo añadió la Task 5 para el tipo de retorno de `listConversations`):

```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
```

Añadir estos métodos a la clase:

```java
    @GetMapping("/{conversationId}/messages")
    public PrivateMessageHistoryResponse listMessages(
            @PathVariable String familyId,
            @PathVariable String conversationId,
            @RequestParam(required = false) @Size(max = 36) String before,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            Authentication authentication
    ) {
        return chatService.listHistory(conversationId, authentication.getName(), before, limit);
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public PrivateMessageResponse sendMessage(
            @PathVariable String familyId,
            @PathVariable String conversationId,
            @Valid @RequestBody SendPrivateMessageRequest request,
            Authentication authentication
    ) {
        return chatService.sendMessage(conversationId, authentication.getName(), request);
    }

    @PostMapping(value = "/{conversationId}/messages/images", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PrivateMessageResponse sendImageMessage(
            @PathVariable String familyId,
            @PathVariable String conversationId,
            @RequestPart(required = false) @Size(max = 36) String id,
            @RequestPart(required = false) @Size(max = 2000) String body,
            @RequestPart("files") List<MultipartFile> files,
            Authentication authentication
    ) {
        return chatService.sendImageMessage(conversationId, authentication.getName(), id, body, files);
    }
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=PrivateChatControllerTest test
```

Expected: PASS, 9/9 (4 de la Task 5 + 5 nuevos: envío/historial/idempotencia/imagen/rate-limit).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatService.java \
        backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatController.java \
        backend/src/test/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatControllerTest.java
git commit -m "feat(backend): enviar texto/imagenes e historial de chat privado"
```

**Nota para la Task 9 (Upload):** el endpoint `GET /uploads/dm/{filename}` que sirve estas imágenes se implementa en la Task 9, no aquí — el test `sendsImageMessageAndServesAttachmentOnlyToParticipants` de este task quedará en rojo hasta la Task 9. Si `subagent-driven-development` ejecuta las tareas en orden, esto es aceptable (Task 9 es la única que lo arregla y va antes del cierre); si el orden no es estrictamente secuencial, marcar este test como pendiente hasta que la Task 9 esté hecha.

---

### Task 7: `PrivateChatService`/`PrivateChatController` — editar, borrar, limpiar, exportar

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatService.java`
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatController.java`
- Modify: `backend/src/test/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatControllerTest.java`

**Interfaces:**
- Consumes: `requireParticipantConversation`, `publishAfterCommit`, `clearedBefore` de la Task 6 (mismo fichero).
- Produces: `PrivateChatService.editMessage(...)`, `.deleteMessage(...)`, `.clearForUser(...)`, `.exportForUser(...)` — cierran la paridad funcional completa con `ChatService`.

- [ ] **Step 1: Añadir tests (RED)**

Añadir a `PrivateChatControllerTest`:

```java
    @Test
    void editsOwnMessageWithinWindow() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-edit-owner"), "Familia DM Edit");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-edit-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String messageId = sendText(owner, conversationId, "original");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}",
                        owner.familyId(), conversationId, messageId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "editado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("editado"));
    }

    @Test
    void blocksEditingAnotherParticipantsMessage() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-edit-block-owner"), "Familia DM Edit Block");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-edit-block-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String messageId = sendText(owner, conversationId, "de owner");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}",
                        owner.familyId(), conversationId, messageId)
                        .header("Authorization", "Bearer " + guest.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "intento ajeno"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void softDeletesOwnMessage() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-delete-owner"), "Familia DM Delete");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-delete-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String messageId = sendText(owner, conversationId, "a borrar");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}",
                        owner.familyId(), conversationId, messageId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.body").doesNotExist());
    }

    @Test
    void clearHidesHistoryOnlyForClearingUser() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-clear-owner"), "Familia DM Clear");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-clear-guest"));
        String conversationId = createConversation(owner, guest.userId());
        sendText(owner, conversationId, "antes de limpiar");

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/clear",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void exportsVisibleMessagesAscending() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-export-owner"), "Familia DM Export");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-export-guest"));
        String conversationId = createConversation(owner, guest.userId());
        sendText(owner, conversationId, "primero");
        sendText(guest, conversationId, "segundo");

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/export",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMessages").value(2))
                .andExpect(jsonPath("$.messages[0].body").value("primero"))
                .andExpect(jsonPath("$.messages[1].body").value("segundo"));
    }

    private String sendText(RegisteredUser sender, String conversationId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        sender.familyId(), conversationId)
                        .header("Authorization", "Bearer " + sender.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "%s"}
                                """.formatted(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get("id").asText();
    }
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=PrivateChatControllerTest test
```

Expected: FAIL — `editMessage`/`deleteMessage`/`clearForUser`/`exportForUser` no existen todavía.

- [ ] **Step 3: Añadir a `PrivateChatService.java`**

Añadir este import:

```java
import java.time.Duration;
```

Añadir esta constante:

```java
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
```

Añadir estos métodos (después de `sendImageMessage`, antes de `publishAfterCommit`):

```java
    @Transactional
    public PrivateMessageResponse editMessage(
            String conversationId,
            String userId,
            String messageId,
            EditPrivateMessageRequest request
    ) {
        PrivateConversationEntity conversation = requireParticipantConversation(conversationId, userId);
        PrivateMessageEntity message = findOwnEditableMessage(conversation.getId(), userId, messageId);
        if (message.getCreatedAt().plus(EDIT_WINDOW).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Message edit window has expired");
        }

        message.editBody(normalizeRequiredBody(request.body()));
        PrivateMessageEntity saved = messageRepository.save(message);
        PrivateMessageResponse response = PrivateMessageResponse.from(saved);
        publishAfterCommit(response, conversation.otherParticipant(userId), null);
        return response;
    }

    @Transactional
    public PrivateMessageResponse deleteMessage(String conversationId, String userId, String messageId) {
        PrivateConversationEntity conversation = requireParticipantConversation(conversationId, userId);
        PrivateMessageEntity message = findOwnEditableMessage(conversation.getId(), userId, messageId);
        message.softDelete();
        PrivateMessageEntity saved = messageRepository.save(message);
        PrivateMessageResponse response = PrivateMessageResponse.from(saved);
        publishAfterCommit(response, conversation.otherParticipant(userId), null);
        return response;
    }

    @Transactional
    public void clearForUser(String conversationId, String userId) {
        PrivateConversationEntity conversation = requireParticipantConversation(conversationId, userId);
        Instant now = Instant.now();
        PrivateMessageClearEntity clear = clearRepository
                .findByConversation_IdAndUser_Id(conversation.getId(), userId).orElse(null);
        if (clear == null) {
            UserEntity user = userRepository.findByIdAndDeletedFalse(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            clear = new PrivateMessageClearEntity(conversation, user, now);
        } else {
            clear.setClearedBefore(now);
        }
        clearRepository.save(clear);
    }

    @Transactional(readOnly = true)
    public PrivateMessageExportResponse exportForUser(String conversationId, String userId) {
        PrivateConversationEntity conversation = requireParticipantConversation(conversationId, userId);
        Instant clearedBefore = clearedBefore(conversation.getId(), userId);
        List<PrivateMessageResponse> messages = messageRepository
                .findVisibleForExport(conversation.getId(), clearedBefore)
                .stream()
                .map(PrivateMessageResponse::from)
                .toList();
        return new PrivateMessageExportResponse(conversation.getId(), Instant.now(), messages.size(), messages);
    }

    private String normalizeRequiredBody(String body) {
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message body is blank");
        }
        String text = body.trim();
        if (text.length() > MAX_BODY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message body is too long");
        }
        return text;
    }

    private PrivateMessageEntity findOwnEditableMessage(String conversationId, String userId, String messageId) {
        PrivateMessageEntity message = messageRepository
                .findByIdAndConversation_IdAndDeletedFalse(messageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getAuthorUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        return message;
    }
```

- [ ] **Step 4: Añadir a `PrivateChatController.java`**

Añadir estos imports:

```java
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
```

Añadir estos métodos:

```java
    @PutMapping("/{conversationId}/messages/{messageId}")
    public PrivateMessageResponse editMessage(
            @PathVariable String familyId,
            @PathVariable String conversationId,
            @PathVariable @Size(max = 36) String messageId,
            @Valid @RequestBody EditPrivateMessageRequest request,
            Authentication authentication
    ) {
        return chatService.editMessage(conversationId, authentication.getName(), messageId, request);
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public PrivateMessageResponse deleteMessage(
            @PathVariable String familyId,
            @PathVariable String conversationId,
            @PathVariable @Size(max = 36) String messageId,
            Authentication authentication
    ) {
        return chatService.deleteMessage(conversationId, authentication.getName(), messageId);
    }

    @PostMapping("/{conversationId}/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearConversation(
            @PathVariable String familyId,
            @PathVariable String conversationId,
            Authentication authentication
    ) {
        chatService.clearForUser(conversationId, authentication.getName());
    }

    @GetMapping("/{conversationId}/export")
    public PrivateMessageExportResponse exportConversation(
            @PathVariable String familyId,
            @PathVariable String conversationId,
            Authentication authentication
    ) {
        return chatService.exportForUser(conversationId, authentication.getName());
    }
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=PrivateChatControllerTest test
```

Expected: PASS, 14/14 (9 de tasks previas + 5 nuevos; `sendsImageMessageAndServesAttachmentOnlyToParticipants` de la Task 6 sigue en rojo hasta la Task 9 — es esperado, ver nota al final de la Task 6).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatService.java \
        backend/src/main/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatController.java \
        backend/src/test/java/org/gipsybuho/recetasfamiliares/dm/PrivateChatControllerTest.java
git commit -m "feat(backend): editar, borrar, limpiar y exportar chat privado"
```

---

### Task 8: Autorizar topics STOMP `/topic/conversations/{id}` y `/topic/users/{userId}/inbox`

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptor.java`
- Modify: `backend/src/test/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptorTest.java`

**Interfaces:**
- Consumes: `PrivateConversationRepository.existsByIdAndParticipant(String, String)` (Task 2), `PrivateConversationRealtimePublisher.conversationTopicFor`/`inboxTopicFor` (Task 4, para que los tests usen las mismas cadenas que el interceptor).
- Produces: nada nuevo consumido por otras tareas — es el punto donde se cierra la autorización de los dos topics.

Spring solo permite **un** interceptor STOMP por canal (`WebSocketConfig.configureClientInboundChannel` registra únicamente `authChannelInterceptor`), así que esta autorización se añade al interceptor existente en vez de crear uno nuevo. El cambio es aditivo: la lógica de `/topic/families/**` (chat y presencia) no se toca.

- [ ] **Step 1: Añadir tests (RED)**

Añadir a `ChatStompAuthChannelInterceptorTest` (imports adicionales al principio del fichero):

```java
import org.gipsybuho.recetasfamiliares.dm.PrivateConversationRepository;
```

Añadir estos campos (junto a los ya existentes) y actualizar `setUp()`:

```java
    private static final String CONVERSATION_ID = "conv-xyz";
    private static final String OTHER_USER_ID = "user-456";
    private static final String CONVERSATION_TOPIC = "/topic/conversations/" + CONVERSATION_ID;
    private static final String INBOX_TOPIC = "/topic/users/" + USER_ID + "/inbox";

    private PrivateConversationRepository privateConversationRepository;
```

En `setUp()`, añadir la línea de mock y actualizar la construcción del interceptor:

```java
        privateConversationRepository = Mockito.mock(PrivateConversationRepository.class);
        interceptor = new ChatStompAuthChannelInterceptor(
                jwtService, familyMemberRepository, presenceRegistry, presencePublisher, privateConversationRepository);
```

Añadir estos tests nuevos al final de la clase (antes de los helpers privados `send`/`connect`/`subscribe`):

```java
    @Test
    void allowsSubscribeToConversationForParticipant() {
        when(privateConversationRepository.existsByIdAndParticipant(CONVERSATION_ID, USER_ID)).thenReturn(true);
        Message<byte[]> subscribe = subscribe(CONVERSATION_TOPIC, new StompPrincipal(USER_ID));

        assertDoesNotThrow(() -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeToConversationForNonParticipant() {
        when(privateConversationRepository.existsByIdAndParticipant(CONVERSATION_ID, USER_ID)).thenReturn(false);
        Message<byte[]> subscribe = subscribe(CONVERSATION_TOPIC, new StompPrincipal(USER_ID));

        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void allowsSubscribeToOwnInboxTopic() {
        Message<byte[]> subscribe = subscribe(INBOX_TOPIC, new StompPrincipal(USER_ID));

        assertDoesNotThrow(() -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeToAnotherUsersInboxTopic() {
        // USER_ID intenta suscribirse a la bandeja de OTHER_USER_ID: debe rechazarse
        // sin siquiera consultar el repositorio de conversaciones.
        Message<byte[]> subscribe = subscribe("/topic/users/" + OTHER_USER_ID + "/inbox", new StompPrincipal(USER_ID));

        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
        Mockito.verifyNoInteractions(privateConversationRepository);
    }
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
cd backend
mvn -q -Dtest=ChatStompAuthChannelInterceptorTest test
```

Expected: FAIL — el constructor de 4 argumentos no acepta un 5º; `/topic/conversations/**` y `/topic/users/**/inbox` caen hoy en `rejectsSubscribeToForeignDestination`.

- [ ] **Step 3: Modificar `ChatStompAuthChannelInterceptor.java`**

Añadir el import:

```java
import org.gipsybuho.recetasfamiliares.dm.PrivateConversationRepository;
```

Añadir estas constantes junto a `TOPIC_PREFIX`/`CHAT_SUFFIX`/`PRESENCE_SUFFIX`:

```java
    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";
    private static final String INBOX_TOPIC_PREFIX = "/topic/users/";
    private static final String INBOX_TOPIC_SUFFIX = "/inbox";
```

Añadir el campo y actualizar el constructor:

```java
    private final PrivateConversationRepository privateConversationRepository;

    public ChatStompAuthChannelInterceptor(
            JwtService jwtService,
            FamilyMemberRepository familyMemberRepository,
            PresenceRegistry presenceRegistry,
            @Lazy PresencePublisher presencePublisher,
            PrivateConversationRepository privateConversationRepository
    ) {
        this.jwtService = jwtService;
        this.familyMemberRepository = familyMemberRepository;
        this.presenceRegistry = presenceRegistry;
        this.presencePublisher = presencePublisher;
        this.privateConversationRepository = privateConversationRepository;
    }
```

Reemplazar el método `authorizeSubscription` completo por:

```java
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String userId = currentUserId(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("Subscription destination not allowed");
        }
        if (destination.startsWith(TOPIC_PREFIX)) {
            authorizeFamilyTopic(accessor, userId, destination);
        } else if (destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
            authorizeConversationTopic(userId, destination);
        } else if (destination.startsWith(INBOX_TOPIC_PREFIX) && destination.endsWith(INBOX_TOPIC_SUFFIX)) {
            authorizeInboxTopic(userId, destination);
        } else {
            throw new MessagingException("Subscription destination not allowed");
        }
    }

    private void authorizeFamilyTopic(StompHeaderAccessor accessor, String userId, String destination) {
        String familyId = extractFamilyId(destination);
        if (familyId == null) {
            throw new MessagingException("Subscription destination not allowed");
        }
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new MessagingException("Family subscription denied");
        }
        if (destination.endsWith(PRESENCE_SUFFIX)) {
            presenceRegistry.subscribe(accessor.getSessionId(), familyId, userId);
            presencePublisher.publish(familyId);
        }
    }

    private void authorizeConversationTopic(String userId, String destination) {
        String conversationId = destination.substring(CONVERSATION_TOPIC_PREFIX.length());
        if (conversationId.isBlank() || conversationId.contains("/")) {
            throw new MessagingException("Subscription destination not allowed");
        }
        if (!privateConversationRepository.existsByIdAndParticipant(conversationId, userId)) {
            throw new MessagingException("Conversation subscription denied");
        }
    }

    private void authorizeInboxTopic(String userId, String destination) {
        String targetUserId = destination.substring(
                INBOX_TOPIC_PREFIX.length(), destination.length() - INBOX_TOPIC_SUFFIX.length());
        // Solo el propio usuario puede suscribirse a su bandeja: sin esto, cualquier
        // sesion autenticada podria enterarse de que otro usuario recibio un mensaje
        // privado nuevo (metadata, no contenido, pero sigue siendo informacion ajena).
        if (!targetUserId.equals(userId)) {
            throw new MessagingException("Inbox subscription denied");
        }
    }
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

```bash
cd backend
mvn -q -Dtest=ChatStompAuthChannelInterceptorTest test
```

Expected: PASS, 16/16 (12 existentes + 4 nuevos).

- [ ] **Step 5: Verificar que `WebSocketConfig` sigue compilando (constructor cambiado)**

```bash
cd backend
mvn -q -DskipTests compile
```

Expected: `BUILD SUCCESS` — `WebSocketConfig` solo inyecta `ChatStompAuthChannelInterceptor` por tipo (Spring resuelve el nuevo parámetro del constructor automáticamente vía el bean `PrivateConversationRepository` ya registrado por Spring Data JPA), no requiere cambios propios.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptor.java \
        backend/src/test/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptorTest.java
git commit -m "feat(backend): autoriza topics STOMP de conversacion privada e inbox"
```

---

### Task 9: Servir imágenes de chat privado (`/uploads/dm/**`) con autorización por conversación

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/photos/UploadController.java`

**Interfaces:**
- Consumes: `PrivateMessageAttachmentRepository.findOwningConversationIdsByStoragePath(String)` (Task 2), `PrivateConversationRepository.existsByIdAndParticipant(String, String)` (Task 2).
- Produces: cierra el test `sendsImageMessageAndServesAttachmentOnlyToParticipants` dejado en rojo en la Task 6.

**Punto de seguridad crítico de este task:** el patrón existente `requireChatAttachmentAccess` autoriza por **membership de familia** (cualquier miembro de la familia puede ver una foto del chat familiar — correcto para ese caso). Para chat privado, reutilizar ese mismo patrón filtraría fotos privadas a **toda la familia**, no solo a los dos participantes. Este task usa `existsByIdAndParticipant`, no `existsByFamily_IdAndUser_IdAndDeletedFalse`.

**Nota sobre el fixture existente:** `UploadControllerTest` (verificado por lectura directa) ya tiene `private record RegisteredUser(String accessToken, String familyId) {}` (líneas 209-210) y un helper `validJpeg()` (línea 200) que genera un JPEG real y decodificable — **no** usar bytes con solo cabecera mágica (`FileStorageService.storeWithThumbnail` decodifica de verdad para el thumbnail). El `RegisteredUser` actual no expone `userId`, así que este task debe **ampliar el record y el helper `register()` existentes** (no crear uno nuevo) para que el test nuevo pueda usar `guest.userId()` como `otherUserId` en la ruta de conversación.

- [ ] **Step 1: Ampliar el fixture de `UploadControllerTest.java`**

Reemplazar el record y el método `register` existentes (líneas 174-193 y 209-210) por:

```java
    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Upload User",
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
```

```java
    private record RegisteredUser(String accessToken, String familyId, String userId) {
    }
```

Confirmar que ningún otro test del fichero se rompe: los tests existentes solo llaman `.accessToken()`/`.familyId()`, así que añadir `userId` es compatible hacia atrás.

- [ ] **Step 2: Añadir el test nuevo**

Añadir a `UploadControllerTest.java` (junto a los tests de `chatImage`/`chatThumbnail` ya existentes):

```java
    @Test
    void privateMessageImageOnlyAccessibleForConversationParticipants() throws Exception {
        RegisteredUser owner = register(uniqueEmail("upload-dm-owner"), "Familia Upload DM");
        String guestEmail = uniqueEmail("upload-dm-guest");
        RegisteredUser guest = register(guestEmail, "Familia Upload DM Guest");
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());
        RegisteredUser outsider = register(uniqueEmail("upload-dm-outsider"), "Familia Upload DM Otra");

        MvcResult conversationResult = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String conversationId = objectMapper.readTree(
                        conversationResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("conversationId").asText();

        MvcResult imageResult = mockMvc.perform(multipart(
                                "/api/v1/families/{familyId}/conversations/{conversationId}/messages/images",
                                owner.familyId(), conversationId)
                        .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", validJpeg()))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();
        String attachmentUrl = objectMapper.readTree(
                        imageResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("attachments").get(0).get("url").asText();
        String path = attachmentUrl.substring(attachmentUrl.indexOf("/uploads/"));

        mockMvc.perform(get(path).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk());
        // outsider comparte la app pero no la conversacion: debe ser 404, aunque
        // no comparta familia con ninguno de los dos participantes.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }
```

`multipart(...)`, `MockMultipartFile`, `get(...)`, `objectMapper` y `validJpeg()` ya están importados/definidos en este fichero (confirmado por lectura directa) — no añadir imports nuevos para este test.

- [ ] **Step 3: Ejecutar y verificar que falla**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=UploadControllerTest test
```

Expected: FAIL — no existe `/uploads/dm/**` todavía (404 genérico de Spring, no el 404 explícito de autorización).

- [ ] **Step 4: Modificar `UploadController.java`**

Añadir estos imports:

```java
import org.gipsybuho.recetasfamiliares.dm.PrivateConversationRepository;
import org.gipsybuho.recetasfamiliares.dm.PrivateMessageAttachmentRepository;
```

Añadir estos campos y actualizar el constructor:

```java
    private final PrivateMessageAttachmentRepository privateMessageAttachmentRepository;
    private final PrivateConversationRepository privateConversationRepository;

    public UploadController(
            @Value("${app.upload.dir:./uploads}") String uploadDirPath,
            @Value("${app.upload.base-url:http://localhost:8080}") String uploadBaseUrl,
            RecipePhotoRepository photoRepository,
            ChatAttachmentRepository chatAttachmentRepository,
            PrivateMessageAttachmentRepository privateMessageAttachmentRepository,
            PrivateConversationRepository privateConversationRepository,
            UserRepository userRepository,
            FamilyMemberRepository familyMemberRepository,
            FamilyRepository familyRepository
    ) {
        this.uploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
        this.uploadBaseUrl = trimTrailingSlash(uploadBaseUrl);
        this.photoRepository = photoRepository;
        this.chatAttachmentRepository = chatAttachmentRepository;
        this.privateMessageAttachmentRepository = privateMessageAttachmentRepository;
        this.privateConversationRepository = privateConversationRepository;
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
    }
```

Añadir estos endpoints (junto a `chatImage`/`chatThumbnail`):

```java
    @GetMapping("/uploads/dm/{filename}")
    public ResponseEntity<byte[]> privateMessageImage(@PathVariable String filename, Authentication authentication) {
        requireSafeFilename(filename);
        requirePrivateAttachmentAccess("/uploads/dm/" + filename, authentication.getName());
        return serveFile(uploadDir.resolve("dm").resolve(filename), filename);
    }

    @GetMapping("/uploads/dm_thumbnails/{filename}")
    public ResponseEntity<byte[]> privateMessageThumbnail(@PathVariable String filename, Authentication authentication) {
        requireSafeFilename(filename);
        requirePrivateAttachmentAccess("/uploads/dm_thumbnails/" + filename, authentication.getName());
        return serveFile(uploadDir.resolve("dm_thumbnails").resolve(filename), filename);
    }
```

Añadir este método privado (junto a `requireChatAttachmentAccess`):

```java
    /**
     * A diferencia de requireChatAttachmentAccess (membership de familia,
     * correcto para el chat familiar compartido), esta comprobacion es por
     * PARTICIPANTE de la conversacion: el resto de la familia no debe poder
     * ver fotos de una conversacion privada ajena aunque comparta familia con
     * uno de los dos participantes.
     */
    private void requirePrivateAttachmentAccess(String storagePath, String requesterId) {
        List<String> owningConversationIds = privateMessageAttachmentRepository
                .findOwningConversationIdsByStoragePath(storagePath);
        boolean allowed = owningConversationIds.stream()
                .anyMatch(conversationId -> privateConversationRepository
                        .existsByIdAndParticipant(conversationId, requesterId));
        if (!allowed) {
            throw notFound();
        }
    }
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=UploadControllerTest test
```

Expected: PASS (todos los tests existentes de `UploadControllerTest` + el nuevo).

- [ ] **Step 6: Re-ejecutar `PrivateChatControllerTest` completo (cierra el test dejado en rojo en la Task 6)**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q -Dtest=PrivateChatControllerTest test
```

Expected: PASS, 14/14, incluido `sendsImageMessageAndServesAttachmentOnlyToParticipants`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/photos/UploadController.java \
        backend/src/test/java/org/gipsybuho/recetasfamiliares/photos/UploadControllerTest.java
git commit -m "feat(backend): sirve imagenes de chat privado con autorizacion por conversacion"
```

---

### Task 10: Verificación final backend

**Files:** ninguno (solo validación).

- [ ] **Step 1: Suite completa backend contra la base de test real**

```bash
cd backend
set -a && source <(grep -v '^#' ../herztner/recetas_app.env | tr -d '\r') && set +a && mvn -q test
```

Expected: sin fallos nuevos en `dm/`, `chat/` (`ChatStompAuthChannelInterceptorTest`), `photos/` (`UploadControllerTest`). Los fallos preexistentes conocidos de la base de test compartida (emails fijos de tests antiguos con datos acumulados de sesiones previas, ver `.superpowers/sdd/progress.md` del sprint de presencia) no cuentan como regresión — confirmar comparando el recuento de fallos por clase, no el total global.

- [ ] **Step 2: Confirmar que las clases nuevas están 100% limpias**

```bash
grep "Tests run" backend/target/surefire-reports/org.gipsybuho.recetasfamiliares.dm.*.txt \
     backend/target/surefire-reports/org.gipsybuho.recetasfamiliares.chat.ChatStompAuthChannelInterceptorTest.txt \
     backend/target/surefire-reports/org.gipsybuho.recetasfamiliares.photos.UploadControllerTest.txt
```

Expected: `Failures: 0, Errors: 0` en cada línea.

- [ ] **Step 3: Compilación limpia completa**

```bash
cd backend
mvn -q -DskipTests compile
```

Expected: `BUILD SUCCESS`, sin warnings nuevos relevantes.
