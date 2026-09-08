package platform.zone01.productservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import platform.zone01.productservice.dto.ErrorResponseDTO;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponseDTO body = new ErrorResponseDTO(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleProductNotFoundException(ProductNotFoundException ex,  HttpServletRequest request) {
        return  errorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NotProductOwnerException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotProductOwnerException(NotProductOwnerException ex, HttpServletRequest request) {
        return  errorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponseDTO> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponseDTO error = new ErrorResponseDTO(Instant.now(),status.value(), message , request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}", request.getRequestURI(), ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred", request);
    }
}
