package platform.eshop.plaza.productservice.exception;

public class NotProductOwnerException extends RuntimeException {
    public NotProductOwnerException(String message) {
        super(message);
    }
}
