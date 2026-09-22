package platform.eshop.plaza.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import platform.eshop.plaza.orderservice.entity.OrderItem;
import platform.eshop.plaza.orderservice.util.Money;

@AllArgsConstructor
@Getter
public class OrderItemResponseDTO {
    private String productId;
    private String name;
    private double price;
    private int quantity;
    private double lineTotal;

    public static OrderItemResponseDTO from(OrderItem item) {
        return new OrderItemResponseDTO(
                item.getProductId(),
                item.getName(),
                item.getPrice(),
                item.getQuantity(),
                Money.round(item.getPrice() * item.getQuantity()));
    }
}
