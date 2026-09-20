package platform.zone01.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import platform.zone01.commonweb.error.ApiExceptionHandler;
import platform.zone01.commonweb.error.ErrorResponseDTO;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler(CartEmptyException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmptyCart(CartEmptyException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler({InsufficientStockException.class, CartConflictException.class, InvalidOrderStateException.class})
    public ResponseEntity<ErrorResponseDTO> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDTO> handleConcurrentChange(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "The order was changed at the same time, please reload and try again", request);
    }

    @ExceptionHandler(NotOrderParticipantException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotParticipant(NotOrderParticipantException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler({ProductUnavailableException.class, CartItemNotFoundException.class, OrderNotFoundException.class})
    public ResponseEntity<ErrorResponseDTO> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ProductServiceUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> handleProductServiceUnavailable(ProductServiceUnavailableException ex, HttpServletRequest request) {
        log.error("Product service problem at {}: {}", request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Products are temporarily unavailable, please try again", request);
    }
}
