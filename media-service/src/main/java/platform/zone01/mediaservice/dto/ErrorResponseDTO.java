package platform.zone01.mediaservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@Getter
public class ErrorResponseDTO {
    private Instant timestamp;
    private int status;
    private String message;
    private String path;
    private Map<String, String> fieldErrors;

    public ErrorResponseDTO(Instant timestamp, int status, String message, String path) {
        this(timestamp, status, message, path, null);
    }
}