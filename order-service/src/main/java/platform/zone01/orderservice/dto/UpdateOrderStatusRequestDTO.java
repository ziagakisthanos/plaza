package platform.zone01.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import platform.zone01.orderservice.enums.OrderStatus;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UpdateOrderStatusRequestDTO {
    @NotNull(message = "Please choose a status")
    private OrderStatus status;
}
