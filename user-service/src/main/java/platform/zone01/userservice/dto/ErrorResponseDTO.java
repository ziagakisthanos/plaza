package platform.zone01.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@AllArgsConstructor
@Getter
public class ErrorResponseDTO {
    private Instant timestamp;
    private int status;
    private String message;
    private String path;
}