package platform.zone01.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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