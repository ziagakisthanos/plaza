package platform.zone01.productservice.exception;

public class NotProductOwnerException extends RuntimeException {
    public NotProductOwnerException(String message) {
        super(message);
    }
}
