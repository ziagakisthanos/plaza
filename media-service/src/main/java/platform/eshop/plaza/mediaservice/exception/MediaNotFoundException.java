package platform.eshop.plaza.mediaservice.exception;

public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(String message) {
        super(message);
    }
}
