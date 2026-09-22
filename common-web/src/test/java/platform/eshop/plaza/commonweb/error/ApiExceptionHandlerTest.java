package platform.eshop.plaza.commonweb.error;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private static class TestHandler extends ApiExceptionHandler {
        ResponseEntity<ErrorResponseDTO> conflict(String message, MockHttpServletRequest request) {
            return error(HttpStatus.CONFLICT, message, request);
        }
    }

    private final TestHandler handler = new TestHandler();
    private final MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/orders/checkout");
    private final WebRequest request = new ServletWebRequest(servletRequest);

    private Method sampleMethod() throws NoSuchMethodException {
        return String.class.getMethod("startsWith", String.class);
    }

    private ParameterValidationResult blankParameter() throws NoSuchMethodException {
        return new ParameterValidationResult(
                sampleParameter(), " ", List.of(new DefaultMessageSourceResolvable("must not be blank")));
    }

    private MethodParameter sampleParameter() throws NoSuchMethodException {
        return new MethodParameter(sampleMethod(), 0);
    }

    private ErrorResponseDTO handle(Exception exception, HttpStatus expectedStatus) throws Exception {
        ResponseEntity<Object> response = handler.handleException(exception, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isInstanceOf(ErrorResponseDTO.class);
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(body.getPath()).isEqualTo("/orders/checkout");
        assertThat(body.getTimestamp()).isNotNull();
        return body;
    }

    @Test
    void unknownUrl_isNotFound() throws Exception {
        ErrorResponseDTO body = handle(new NoResourceFoundException(HttpMethod.GET, "orders/a/b"), HttpStatus.NOT_FOUND);

        assertThat(body.getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void noHandler_isNotFound() throws Exception {
        ErrorResponseDTO body = handle(new NoHandlerFoundException("GET", "/x", new HttpHeaders()), HttpStatus.NOT_FOUND);

        assertThat(body.getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void unreadableBody_isBadRequest() throws Exception {
        ErrorResponseDTO body = handle(
                new HttpMessageNotReadableException("JSON parse error: bad", new MockHttpInputMessage(new byte[0])),
                HttpStatus.BAD_REQUEST);

        assertThat(body.getMessage()).isEqualTo("Malformed request body");
    }

    @Test
    void wrongMethod_isMethodNotAllowed_andSaysWhatIsAllowed() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "PUT")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.PUT);
        ErrorResponseDTO body = (ErrorResponseDTO) response.getBody();
        assertThat(body.getMessage()).isEqualTo("Method 'PATCH' is not supported for this URL");
    }

    @Test
    void wrongContentType_isUnsupportedMediaType() throws Exception {
        ErrorResponseDTO body = handle(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        assertThat(body.getMessage()).isEqualTo("Content type 'text/plain' is not supported");
    }

    @Test
    void missingContentType_isUnsupportedMediaType() throws Exception {
        ErrorResponseDTO body = handle(
                new HttpMediaTypeNotSupportedException((MediaType) null, List.of(MediaType.APPLICATION_JSON)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        assertThat(body.getMessage()).isEqualTo("The request has no content type");
    }

    @Test
    void unavailableResponseFormat_isNotAcceptable() throws Exception {
        ErrorResponseDTO body = handle(new HttpMediaTypeNotAcceptableException("no converter"), HttpStatus.NOT_ACCEPTABLE);

        assertThat(body.getMessage()).isEqualTo("The requested response format is not available");
    }

    @Test
    void missingParameter_isBadRequest_andNamesIt() throws Exception {
        ErrorResponseDTO body = handle(new MissingServletRequestParameterException("productId", "String"), HttpStatus.BAD_REQUEST);

        assertThat(body.getMessage()).isEqualTo("Required parameter 'productId' is missing");
    }

    @Test
    void missingPart_isBadRequest_andNamesIt() throws Exception {
        ErrorResponseDTO body = handle(new MissingServletRequestPartException("file"), HttpStatus.BAD_REQUEST);

        assertThat(body.getMessage()).isEqualTo("Required part 'file' is missing");
    }

    @Test
    void parameterOfTheWrongType_isBadRequest_andNamesItAndTheValue() throws Exception {
        MethodArgumentTypeMismatchException mismatch =
                new MethodArgumentTypeMismatchException("LOST", Integer.class, "status", sampleParameter(), new IllegalArgumentException());

        ErrorResponseDTO body = handle(mismatch, HttpStatus.BAD_REQUEST);

        assertThat(body.getMessage()).isEqualTo("Invalid value 'LOST' for parameter 'status'");
    }

    @Test
    void fileTooLarge_isPayloadTooLarge() throws Exception {
        ErrorResponseDTO body = handle(new MaxUploadSizeExceededException(2_097_152), HttpStatus.PAYLOAD_TOO_LARGE);

        assertThat(body.getMessage()).isEqualTo("The uploaded file is too large");
    }

    @Test
    void invalidMethodParameters_areBadRequest() throws Exception {
        ErrorResponseDTO body = handle(new HandlerMethodValidationException(MethodValidationResult.create(this, sampleMethod(), List.of(blankParameter()))), HttpStatus.BAD_REQUEST);

        assertThat(body.getMessage()).isEqualTo("Validation failed");
    }

    @Test
    void invalidBody_listsEveryFieldThatIsWrong() throws Exception {
        BindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError("request", "name", "Name is required"));
        result.addError(new FieldError("request", "price", "Price must be greater than 0"));

        ErrorResponseDTO body = handle(new MethodArgumentNotValidException(sampleParameter(), result), HttpStatus.BAD_REQUEST);

        assertThat(body.getMessage()).isEqualTo("Validation failed");
        assertThat(body.getFieldErrors())
                .containsEntry("name", "Name is required")
                .containsEntry("price", "Price must be greater than 0")
                .hasSize(2);
    }

    @Test
    void otherWebErrors_useTheNameOfTheStatusAsTheMessage() throws Exception {
        ErrorResponseDTO body = handle(new ServletRequestBindingException("cannot bind"), HttpStatus.BAD_REQUEST);

        assertThat(body.getMessage()).isEqualTo("Bad Request");
    }

    @Test
    void anythingUnexpected_isAPlainServerError_thatDoesNotLeakDetails() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleUnexpected(new IllegalStateException("password=hunter2 in stack"), servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Unexpected error occurred");
        assertThat(response.getBody().getPath()).isEqualTo("/orders/checkout");
        assertThat(response.getBody().getMessage()).doesNotContain("hunter2");
    }

    @Test
    void error_buildsTheStandardBodyForServiceSpecificHandlers() {
        ResponseEntity<ErrorResponseDTO> response = handler.conflict("Not enough stock", servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).isEqualTo("Not enough stock");
        assertThat(response.getBody().getPath()).isEqualTo("/orders/checkout");
        assertThat(response.getBody().getFieldErrors()).isNull();
    }
}
