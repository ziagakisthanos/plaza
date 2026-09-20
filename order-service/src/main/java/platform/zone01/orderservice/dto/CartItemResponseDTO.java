package platform.zone01.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CartItemResponseDTO {
    private String productId;
    private String name;
    private double price;
    private int quantity;
    private int availableStock;
    private double lineTotal;
}
