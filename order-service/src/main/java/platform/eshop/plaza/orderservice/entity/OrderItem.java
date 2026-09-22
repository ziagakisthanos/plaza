package platform.eshop.plaza.orderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OrderItem {
    private String productId;
    private String name;
    private double price;
    private int quantity;
}
