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
