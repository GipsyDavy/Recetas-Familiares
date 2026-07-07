package org.gipsybuho.recetasfamiliares.chat;

import java.time.Instant;
import java.util.List;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 30;

    private final ChatMessageRepository messageRepository;
    private final ChatMessageClearRepository clearRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final ChatSendRateLimiter rateLimiter;
    private final ChatRealtimePublisher realtimePublisher;

    public ChatService(
            ChatMessageRepository messageRepository,
            ChatMessageClearRepository clearRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            UserRepository userRepository,
            ChatSendRateLimiter rateLimiter,
            ChatRealtimePublisher realtimePublisher
    ) {
        this.messageRepository = messageRepository;
        this.clearRepository = clearRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.realtimePublisher = realtimePublisher;
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
        realtimePublisher.publishMessage(response);
        return response;
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
}
