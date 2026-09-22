package platform.eshop.plaza.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class SellerStatsDTO {
    private double totalEarned;
    private int orderCount;
    private List<ProductStatDTO> bestSelling;
}
