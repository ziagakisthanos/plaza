package platform.eshop.plaza.orderservice.enums;

import java.util.Optional;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public Optional<OrderStatus> next() {
        return switch (this) {
            case PENDING -> Optional.of(CONFIRMED);
            case CONFIRMED -> Optional.of(SHIPPED);
            case SHIPPED -> Optional.of(DELIVERED);
            case DELIVERED, CANCELLED -> Optional.empty();
        };
    }

    public boolean canBeCancelled() {
        return this == PENDING || this == CONFIRMED;
    }
}
