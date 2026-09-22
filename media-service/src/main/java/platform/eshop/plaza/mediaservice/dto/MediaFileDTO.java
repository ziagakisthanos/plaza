package platform.eshop.plaza.mediaservice.dto;

import java.util.Arrays;
import java.util.Objects;

public record MediaFileDTO(
        byte[] bytes,
        String contentType
) {

    @Override
    public boolean equals(Object other) {
        return other instanceof MediaFileDTO(byte[] otherBytes, String otherType)
                && Arrays.equals(bytes, otherBytes)
                && Objects.equals(contentType, otherType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + Objects.hashCode(contentType);
    }

    @Override
    public String toString() {
        return "MediaFileDTO[bytes=" + Arrays.toString(bytes) + ", contentType=" + contentType + "]";
    }
}
