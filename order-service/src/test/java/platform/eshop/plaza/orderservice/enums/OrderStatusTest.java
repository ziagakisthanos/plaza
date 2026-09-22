package platform.eshop.plaza.orderservice.enums;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void next_followsTheLifecycle() {
        assertThat(OrderStatus.PENDING.next()).contains(OrderStatus.CONFIRMED);
        assertThat(OrderStatus.CONFIRMED.next()).contains(OrderStatus.SHIPPED);
        assertThat(OrderStatus.SHIPPED.next()).contains(OrderStatus.DELIVERED);
    }

    @Test
    void next_isEmpty_forTheFinalStatuses() {
        assertThat(OrderStatus.DELIVERED.next()).isEqualTo(Optional.empty());
        assertThat(OrderStatus.CANCELLED.next()).isEqualTo(Optional.empty());
    }

    @Test
    void onlyPendingAndConfirmedOrdersCanBeCancelled() {
        assertThat(OrderStatus.PENDING.canBeCancelled()).isTrue();
        assertThat(OrderStatus.CONFIRMED.canBeCancelled()).isTrue();
        assertThat(OrderStatus.SHIPPED.canBeCancelled()).isFalse();
        assertThat(OrderStatus.DELIVERED.canBeCancelled()).isFalse();
        assertThat(OrderStatus.CANCELLED.canBeCancelled()).isFalse();
    }
}
