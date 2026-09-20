package platform.zone01.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import platform.zone01.orderservice.entity.Order;
import platform.zone01.orderservice.enums.OrderStatus;
import platform.zone01.orderservice.enums.PaymentMethod;

import java.time.Instant;
import java.util.List;

@AllArgsConstructor
@Getter
public class OrderResponseDTO {
    private String id;
    private String buyerId;
    private String sellerId;
    private List<OrderItemResponseDTO> items;
    private double total;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private String deliveryAddress;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderResponseDTO from(Order order) {
        return new OrderResponseDTO(
                order.getId(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getItems().stream().map(OrderItemResponseDTO::from).toList(),
                order.getTotal(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getDeliveryAddress(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
