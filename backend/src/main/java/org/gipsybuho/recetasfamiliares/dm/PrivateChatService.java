package org.gipsybuho.recetasfamiliares.dm;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.gipsybuho.recetasfamiliares.chat.ChatSendRateLimiter;
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
public class PrivateChatService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_BODY_LENGTH = 2000;
    private static final int MAX_IMAGE_ATTACHMENTS = 5;

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
