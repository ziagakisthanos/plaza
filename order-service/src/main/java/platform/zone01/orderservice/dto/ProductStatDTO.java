package platform.zone01.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ProductStatDTO {
    private String productId;
    private String name;
    private int quantity;
    private double amount;
}
