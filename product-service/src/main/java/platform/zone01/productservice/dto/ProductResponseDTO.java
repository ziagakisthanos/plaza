package platform.zone01.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import platform.zone01.productservice.entity.Product;

import java.time.Instant;

@AllArgsConstructor
@Getter
public class ProductResponseDTO {
    private String id;
    private String name;
    private String description;
    private Double price;
    private Integer quantity;
    private String userId;
    private String category;
    private Instant createdAt;

    public static ProductResponseDTO from(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getUserId(),
                product.getCategory(),
                product.getCreatedAt());
    }
}
