package platform.eshop.plaza.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import platform.eshop.plaza.orderservice.enums.OrderStatus;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UpdateOrderStatusRequestDTO {
    @NotNull(message = "Please choose a status")
    private OrderStatus status;
}
