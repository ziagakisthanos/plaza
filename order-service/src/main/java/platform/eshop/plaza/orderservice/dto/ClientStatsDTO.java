package platform.eshop.plaza.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class ClientStatsDTO {
    private double totalSpent;
    private int orderCount;
    private List<ProductStatDTO> mostBought;
    private List<ProductStatDTO> bestProducts;
}
