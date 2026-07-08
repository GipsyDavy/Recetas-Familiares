package org.gipsybuho.recetasfamiliares.chat;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/families/{familyId}/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/messages")
    public ChatHistoryResponse listMessages(
            @PathVariable String familyId,
            @RequestParam(required = false) @Size(max = 36) String before,
            @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
            Authentication authentication
    ) {
        return chatService.listHistory(familyId, authentication.getName(), before, limit);
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendMessage(
            @PathVariable String familyId,
            @Valid @RequestBody SendChatMessageRequest request,
            Authentication authentication
    ) {
        return chatService.sendMessage(familyId, authentication.getName(), request);
    }

    @PostMapping(value = "/messages/images", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendImageMessage(
            @PathVariable String familyId,
            @RequestPart(required = false) @Size(max = 36) String id,
            @RequestPart(required = false) @Size(max = 2000) String body,
            @RequestPart("files") List<MultipartFile> files,
            Authentication authentication
    ) {
        return chatService.sendImageMessage(familyId, authentication.getName(), id, body, files);
    }

    @PostMapping("/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearChat(
            @PathVariable String familyId,
            Authentication authentication
    ) {
        chatService.clearForUser(familyId, authentication.getName());
    }

    @GetMapping("/export")
    public ChatExportResponse exportChat(
            @PathVariable String familyId,
            Authentication authentication
    ) {
        return chatService.exportForUser(familyId, authentication.getName());
    }
}
