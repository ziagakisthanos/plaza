package platform.zone01.mediaservice.dto;

public record MediaFileDTO (
        byte[] bytes,
        String contentType
) {}

