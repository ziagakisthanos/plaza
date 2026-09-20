package platform.zone01.orderservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import platform.zone01.commonsecurity.jwt.JwtService;
import platform.zone01.orderservice.config.SecurityConfig;
import platform.zone01.orderservice.dto.CheckoutRequestDTO;
import platform.zone01.orderservice.dto.OrderItemResponseDTO;
import platform.zone01.orderservice.dto.OrderResponseDTO;
import platform.zone01.orderservice.enums.OrderStatus;
import platform.zone01.orderservice.enums.PaymentMethod;
import platform.zone01.orderservice.exception.CartEmptyException;
import platform.zone01.orderservice.exception.InsufficientStockException;
import platform.zone01.orderservice.exception.ProductServiceUnavailableException;
import platform.zone01.orderservice.exception.ProductUnavailableException;
import platform.zone01.orderservice.service.OrderService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class OrderControllerCheckoutTest {

    private static final String BODY = "{\"paymentMethod\":\"PAY_ON_DELIVERY\",\"deliveryAddress\":\"12 Main Street\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OrderService orderService;

    private String bearer(String userId, String role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }

    private static OrderResponseDTO order() {
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        return new OrderResponseDTO("o1", "client-1", "seller-1",
                List.of(new OrderItemResponseDTO("p1", "Book", 12.5, 2, 25.0)),
                25.0, OrderStatus.PENDING, PaymentMethod.PAY_ON_DELIVERY, "12 Main Street", now, now);
    }

    @Test
    void checkout_answersCreated_withTheOrders() throws Exception {
        when(orderService.checkout(eq("client-1"), any(CheckoutRequestDTO.class))).thenReturn(List.of(order()));

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value("o1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].paymentMethod").value("PAY_ON_DELIVERY"))
                .andExpect(jsonPath("$[0].total").value(25.0))
                .andExpect(jsonPath("$[0].items[0].name").value("Book"))
                .andExpect(jsonPath("$[0].items[0].lineTotal").value(25.0));
    }

    @Test
    void checkout_usesTheIdentityFromTheToken_notFromTheBody() throws Exception {
        when(orderService.checkout(eq("client-1"), any(CheckoutRequestDTO.class))).thenReturn(List.of(order()));

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"PAY_ON_DELIVERY\",\"deliveryAddress\":\"Somewhere\",\"buyerId\":\"someone-else\"}"))
                .andExpect(status().isCreated());

        verify(orderService).checkout(eq("client-1"), any(CheckoutRequestDTO.class));
    }

    @Test
    void checkout_requiresAToken() throws Exception {
        mockMvc.perform(post("/orders/checkout").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void checkout_isForbiddenToSellers() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("seller-1", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }

    @Test
    void checkout_rejectsAMissingAddress() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"PAY_ON_DELIVERY\",\"deliveryAddress\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryAddress").value("Please enter a delivery address"));

        verifyNoInteractions(orderService);
    }

    @Test
    void checkout_rejectsAMissingPaymentMethod() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryAddress\":\"12 Main Street\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.paymentMethod").value("Please choose a payment method"));
    }

    @Test
    void checkout_rejectsAPaymentMethodThatDoesNotExist() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"BITCOIN\",\"deliveryAddress\":\"12 Main Street\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void checkout_rejectsAVeryLongAddress() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"PAY_ON_DELIVERY\",\"deliveryAddress\":\"" + "x".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryAddress")
                        .value("The delivery address must be at most 300 characters"));
    }

    @Test
    void checkout_answersBadRequest_whenTheCartIsEmpty() throws Exception {
        when(orderService.checkout(eq("client-1"), any(CheckoutRequestDTO.class)))
                .thenThrow(new CartEmptyException("Your cart is empty"));

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your cart is empty"));
    }

    @Test
    void checkout_answersConflict_whenThereIsNotEnoughStock() throws Exception {
        when(orderService.checkout(eq("client-1"), any(CheckoutRequestDTO.class)))
                .thenThrow(new InsufficientStockException("Not enough stock for 'Book': only 1 left"));

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock for 'Book': only 1 left"));
    }

    @Test
    void checkout_answersNotFound_whenAProductWasDeleted() throws Exception {
        when(orderService.checkout(eq("client-1"), any(CheckoutRequestDTO.class)))
                .thenThrow(new ProductUnavailableException("Product with id: p1 not found"));

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void checkout_answersServiceUnavailable_whenTheProductServiceIsDown() throws Exception {
        when(orderService.checkout(eq("client-1"), any(CheckoutRequestDTO.class)))
                .thenThrow(new ProductServiceUnavailableException("Product service is unreachable"));

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
