package org.gipsybuho.recetasfamiliares.dm;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
}
