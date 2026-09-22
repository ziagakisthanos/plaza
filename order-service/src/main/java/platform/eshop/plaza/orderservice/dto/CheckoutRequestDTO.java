package platform.eshop.plaza.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import platform.eshop.plaza.orderservice.enums.PaymentMethod;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CheckoutRequestDTO {
    @NotNull(message = "Please choose a payment method")
    private PaymentMethod paymentMethod;

    @NotBlank(message = "Please enter a delivery address")
    @Size(max = 300, message = "The delivery address must be at most 300 characters")
    private String deliveryAddress;
}
