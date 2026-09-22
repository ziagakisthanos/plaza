package platform.eshop.plaza.mediaservice.exception;

public class MaxUploadSizeExceededException extends RuntimeException {
    public MaxUploadSizeExceededException(String message) {
        super(message);
    }
}
