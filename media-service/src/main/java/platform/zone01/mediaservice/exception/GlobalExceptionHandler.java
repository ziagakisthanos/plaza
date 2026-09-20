package platform.zone01.mediaservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import platform.zone01.commonweb.error.ApiExceptionHandler;
import platform.zone01.commonweb.error.ErrorResponseDTO;

@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidImage(InvalidImageException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MediaStorageException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaStorage(MediaStorageException ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaNotFound(MediaNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NotMediaOwnerException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotMediaOwner(NotMediaOwnerException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleFileTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), request);
    }
}
