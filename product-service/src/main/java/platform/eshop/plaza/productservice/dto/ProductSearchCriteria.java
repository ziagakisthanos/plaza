package platform.eshop.plaza.productservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class ProductSearchCriteria {
    private String q;

    private String category;

    @PositiveOrZero(message = "Minimum price cannot be negative")
    private Double minPrice;

    @PositiveOrZero(message = "Maximum price cannot be negative")
    private Double maxPrice;

    private Boolean inStock;

    @Pattern(regexp = "newest|price_asc|price_desc", message = "Sort must be newest, price_asc or price_desc")
    private String sort;

    @AssertTrue(message = "Minimum price cannot be greater than maximum price")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }
}
