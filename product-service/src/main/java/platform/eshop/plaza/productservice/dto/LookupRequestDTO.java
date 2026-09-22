package platform.eshop.plaza.productservice.dto;

import jakarta.validation.constraints.NotBlank;
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
public class LookupRequestDTO {
    @NotEmpty(message = "At least one id is required")
    private List<@NotBlank String> ids;
}
