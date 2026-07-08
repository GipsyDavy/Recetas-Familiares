package org.gipsybuho.recetasfamiliares.chat;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.photos.FileStorageService;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_BODY_LENGTH = 2000;
    private static final int MAX_IMAGE_ATTACHMENTS = 5;
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(15);

    private final ChatMessageRepository messageRepository;
    private final ChatMessageClearRepository clearRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final ChatSendRateLimiter rateLimiter;
    private final ChatRealtimePublisher realtimePublisher;
    private final FileStorageService fileStorageService;

    public ChatService(
            ChatMessageRepository messageRepository,
            ChatMessageClearRepository clearRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            UserRepository userRepository,
            ChatSendRateLimiter rateLimiter,
            ChatRealtimePublisher realtimePublisher,
            FileStorageService fileStorageService
    ) {
        this.messageRepository = messageRepository;
        this.clearRepository = clearRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.realtimePublisher = realtimePublisher;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse listHistory(String familyId, String userId, String before, Integer limit) {
        requireMembership(familyId, userId);
        int pageSize = normalizeLimit(limit);
        Instant clearedBefore = clearedBefore(familyId, userId);

        Instant beforeCreatedAt = null;
        String beforeId = null;
        if (before != null && !before.isBlank()) {
            ChatMessageEntity cursor = messageRepository.findByIdAndFamily_Id(before, familyId).orElse(null);
            if (cursor != null) {
                beforeCreatedAt = cursor.getCreatedAt();
                beforeId = cursor.getId();
            }
        }

        // Se pide una fila extra para detectar si hay pagina anterior.
        Pageable pageable = PageRequest.of(0, pageSize + 1);
        List<ChatMessageEntity> rows = messageRepository.findHistory(
                familyId, clearedBefore, beforeCreatedAt, beforeId, pageable);

        boolean hasMore = rows.size() > pageSize;
        List<ChatMessageEntity> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<ChatMessageResponse> items = pageRows.stream().map(ChatMessageResponse::from).toList();
        String nextBefore = hasMore && !items.isEmpty() ? items.get(items.size() - 1).id() : null;
        return new ChatHistoryResponse(items, hasMore, nextBefore);
    }

    @Transactional
    public ChatMessageResponse sendMessage(String familyId, String userId, SendChatMessageRequest request) {
        requireMembership(familyId, userId);

        String clientId = request.id() == null || request.id().isBlank() ? null : request.id().trim();
        if (clientId != null) {
            ChatMessageEntity existing = messageRepository.findById(clientId).orElse(null);
            if (existing != null) {
                // Idempotencia: reintento con el mismo id no duplica.
                if (existing.getFamilyId().equals(familyId) && existing.getAuthorUserId().equals(userId)) {
                    return ChatMessageResponse.from(existing);
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Message id already used");
            }
        }

        if (!rateLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages, slow down");
        }

        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        UserEntity author = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        ChatMessageEntity message = new ChatMessageEntity(clientId, family, author, request.body().trim());
        ChatMessageEntity saved = messageRepository.save(message);
        ChatMessageResponse response = ChatMessageResponse.from(saved);
        publishAfterCommit(response, null);
        return response;
    }

    @Transactional
    public ChatMessageResponse sendImageMessage(
            String familyId,
            String userId,
            String id,
            String body,
            List<MultipartFile> files
    ) {
        requireMembership(familyId, userId);
        List<MultipartFile> images = normalizeImageFiles(files);
        String clientId = normalizeClientId(id);
        ChatMessageEntity existing = findExistingMessage(clientId, familyId, userId);
        if (existing != null) {
            return ChatMessageResponse.from(existing);
        }

        String normalizedBody = normalizeOptionalBody(body);
        if (!rateLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages, slow down");
        }

        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        UserEntity author = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        ChatMessageEntity message = new ChatMessageEntity(clientId, family, author, normalizedBody);
        List<FileStorageService.StoredFile> storedFiles = new ArrayList<>();
        try {
            for (MultipartFile file : images) {
                FileStorageService.StoredFile stored = storeChatImage(file);
                storedFiles.add(stored);
                message.addAttachment(new ChatAttachmentEntity(
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
            ChatMessageEntity saved = messageRepository.save(message);
            ChatMessageResponse response = ChatMessageResponse.from(saved);
            publishAfterCommit(response, storedFiles);
            return response;
        } catch (RuntimeException e) {
            cleanupStoredFiles(storedFiles);
            throw e;
        }
    }

    @Transactional
    public ChatMessageResponse editMessage(
            String familyId,
            String userId,
            String messageId,
            EditChatMessageRequest request
    ) {
        requireMembership(familyId, userId);
        ChatMessageEntity message = findOwnEditableMessage(familyId, userId, messageId);
        if (message.getCreatedAt().plus(EDIT_WINDOW).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Message edit window has expired");
        }

        message.editBody(normalizeRequiredBody(request.body()));
        ChatMessageEntity saved = messageRepository.save(message);
        ChatMessageResponse response = ChatMessageResponse.from(saved);
        publishAfterCommit(response, null);
        return response;
    }

    @Transactional
    public ChatMessageResponse deleteMessage(String familyId, String userId, String messageId) {
        requireMembership(familyId, userId);
        ChatMessageEntity message = findOwnEditableMessage(familyId, userId, messageId);
        message.softDelete();
        ChatMessageEntity saved = messageRepository.save(message);
        ChatMessageResponse response = ChatMessageResponse.from(saved);
        publishAfterCommit(response, null);
        return response;
    }

    /**
     * Publica el mensaje por WebSocket solo si la transaccion confirma, y limpia
     * los archivos escritos si termina en rollback. Evita broadcasts fantasma y
     * binarios huerfanos cuando el commit falla despues de escribir en disco.
     */
    private void publishAfterCommit(ChatMessageResponse response, List<FileStorageService.StoredFile> storedFiles) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            realtimePublisher.publishMessage(response);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                realtimePublisher.publishMessage(response);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK && storedFiles != null) {
                    cleanupStoredFiles(storedFiles);
                }
            }
        });
    }

    @Transactional
    public void clearForUser(String familyId, String userId) {
        requireMembership(familyId, userId);
        Instant now = Instant.now();
        ChatMessageClearEntity clear = clearRepository.findByFamily_IdAndUser_Id(familyId, userId).orElse(null);
        if (clear == null) {
            FamilyEntity family = familyRepository.findById(familyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
            UserEntity user = userRepository.findByIdAndDeletedFalse(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            clear = new ChatMessageClearEntity(family, user, now);
        } else {
            clear.setClearedBefore(now);
        }
        clearRepository.save(clear);
    }

    @Transactional(readOnly = true)
    public ChatExportResponse exportForUser(String familyId, String userId) {
        requireMembership(familyId, userId);
        Instant clearedBefore = clearedBefore(familyId, userId);
        List<ChatMessageResponse> messages = messageRepository
                .findVisibleForExport(familyId, clearedBefore)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        return new ChatExportResponse(familyId, Instant.now(), messages.size(), messages);
    }

    private Instant clearedBefore(String familyId, String userId) {
        return clearRepository.findByFamily_IdAndUser_Id(familyId, userId)
                .map(ChatMessageClearEntity::getClearedBefore)
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

    private void requireMembership(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
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

    private String normalizeClientId(String id) {
        return id == null || id.isBlank() ? null : id.trim();
    }

    private ChatMessageEntity findOwnEditableMessage(String familyId, String userId, String messageId) {
        ChatMessageEntity message = messageRepository.findByIdAndFamily_IdAndDeletedFalse(messageId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getAuthorUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        return message;
    }

    private ChatMessageEntity findExistingMessage(String clientId, String familyId, String userId) {
        if (clientId == null) {
            return null;
        }
        ChatMessageEntity existing = messageRepository.findById(clientId).orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.getFamilyId().equals(familyId) && existing.getAuthorUserId().equals(userId)) {
            return existing;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Message id already used");
    }

    private FileStorageService.StoredFile storeChatImage(MultipartFile file) {
        try {
            return fileStorageService.storeWithThumbnail(file, "chat", "chat_thumbnails");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store uploaded image");
        }
    }

    private void cleanupStoredFiles(List<FileStorageService.StoredFile> storedFiles) {
        for (FileStorageService.StoredFile stored : storedFiles) {
            fileStorageService.deleteStoredPath(stored.storagePath());
            fileStorageService.deleteStoredPath(stored.thumbnailStoragePath());
        }
    }
}
