package platform.eshop.plaza.productservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class StockRequestDTO {
    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<StockItemDTO> items;
}
