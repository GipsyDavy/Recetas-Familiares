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
