package platform.eshop.plaza.userservice.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super("Email already in use: " + message);
    }
}
