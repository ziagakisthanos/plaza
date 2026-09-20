package platform.zone01.productservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import platform.zone01.commonweb.error.ApiExceptionHandler;
import platform.zone01.commonweb.error.ErrorResponseDTO;

@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NotProductOwnerException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotProductOwner(NotProductOwnerException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }
}
