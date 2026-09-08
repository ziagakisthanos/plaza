package platform.zone01.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ProductResponseDTO {
    private String id;
    private String name;
    private String description;
    private Double price;
    private Integer quantity;
    private String userId;
}
