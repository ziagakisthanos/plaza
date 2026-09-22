package platform.eshop.plaza.commonweb.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every error a web request can run into into the same JSON body. Spring already knows the
 * right status for its own exceptions (unknown URL, wrong method, unreadable body, file too
 * large...), so this class only rewrites the answer into {@link ErrorResponseDTO} with a message
 * a person can read. Anything not recognised becomes a plain 500 without leaking details.
 * Services extend it and add handlers for their own exceptions.
 */
@Slf4j
public abstract class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ErrorResponseDTO error = new ErrorResponseDTO(Instant.now(), status.value(), messageFor(ex, status), pathOf(request));
        return new ResponseEntity<>(error, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponseDTO error = new ErrorResponseDTO(
                Instant.now(), status.value(), "Validation failed", pathOf(request), fieldErrors);
        return new ResponseEntity<>(error, headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}", request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred", request);
    }

    protected ResponseEntity<ErrorResponseDTO> error(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponseDTO body = new ErrorResponseDTO(Instant.now(), status.value(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    private String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }

    private String messageFor(Exception ex, HttpStatus status) {
        if (ex instanceof HttpMessageNotReadableException) {
            return "Malformed request body";
        }
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return "Invalid value '" + mismatch.getValue() + "' for parameter '" + mismatch.getName() + "'";
        }
        if (ex instanceof MissingServletRequestParameterException missing) {
            return "Required parameter '" + missing.getParameterName() + "' is missing";
        }
        if (ex instanceof MissingServletRequestPartException missing) {
            return "Required part '" + missing.getRequestPartName() + "' is missing";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException notSupported) {
            return "Method '" + notSupported.getMethod() + "' is not supported for this URL";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException notSupported) {
            return notSupported.getContentType() == null
                    ? "The request has no content type"
                    : "Content type '" + notSupported.getContentType() + "' is not supported";
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return "The requested response format is not available";
        }
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            return "Resource not found";
        }
        if (ex instanceof MaxUploadSizeExceededException) {
            return "The uploaded file is too large";
        }
        if (ex instanceof HandlerMethodValidationException) {
            return "Validation failed";
        }
        return status.getReasonPhrase();
    }
}
