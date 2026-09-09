package platform.zone01.mediaservice.exception;

public class NotMediaOwnerException extends RuntimeException {
    public NotMediaOwnerException(String message) {
        super(message);
    }
}
