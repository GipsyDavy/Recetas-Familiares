package org.gipsybuho.recetasfamiliares.chat;

public record ChatAttachmentResponse(
        String id,
        String url,
        String thumbnailUrl,
        String contentType,
        long sizeBytes,
        Integer width,
        Integer height
) {
    static ChatAttachmentResponse from(ChatAttachmentEntity attachment) {
        return new ChatAttachmentResponse(
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
