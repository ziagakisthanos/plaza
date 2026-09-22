package platform.eshop.plaza.orderservice.exception;

public class CartConflictException extends RuntimeException {
    public CartConflictException(String message) {
        super(message);
    }
}
