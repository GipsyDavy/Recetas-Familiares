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
