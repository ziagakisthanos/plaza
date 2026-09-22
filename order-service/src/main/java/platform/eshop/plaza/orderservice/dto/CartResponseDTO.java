package platform.eshop.plaza.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class CartResponseDTO {
    private List<CartItemResponseDTO> items;
    private int itemCount;
    private double total;
}
