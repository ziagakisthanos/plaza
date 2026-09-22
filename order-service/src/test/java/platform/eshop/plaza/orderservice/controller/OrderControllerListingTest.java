package platform.eshop.plaza.orderservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import platform.eshop.plaza.commonsecurity.jwt.JwtService;
import platform.eshop.plaza.orderservice.config.SecurityConfig;
import platform.eshop.plaza.orderservice.dto.OrderItemResponseDTO;
import platform.eshop.plaza.orderservice.dto.OrderResponseDTO;
import platform.eshop.plaza.orderservice.enums.OrderStatus;
import platform.eshop.plaza.orderservice.enums.PaymentMethod;
import platform.eshop.plaza.orderservice.exception.NotOrderParticipantException;
import platform.eshop.plaza.orderservice.exception.OrderNotFoundException;
import platform.eshop.plaza.orderservice.service.OrderService;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class OrderControllerListingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OrderService orderService;

    private String bearer(String userId, String role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }

    private static OrderResponseDTO order(String id) {
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        return new OrderResponseDTO(id, "client-1", "seller-1",
                List.of(new OrderItemResponseDTO("p1", "Book", 12.5, 2, 25.0)),
                25.0, OrderStatus.PENDING, PaymentMethod.PAY_ON_DELIVERY, "12 Main Street", now, now);
    }

    @Test
    void myOrders_listsTheOrdersOfTheClient() throws Exception {
        when(orderService.listForBuyer("client-1", null, null)).thenReturn(List.of(order("o2"), order("o1")));

        mockMvc.perform(get("/orders").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("o2"))
                .andExpect(jsonPath("$[1].id").value("o1"))
                .andExpect(jsonPath("$[0].items[0].name").value("Book"));
    }

    @Test
    void myOrders_passesTheSearchAndStatusFilters() throws Exception {
        when(orderService.listForBuyer("client-1", "mug", OrderStatus.SHIPPED)).thenReturn(List.of(order("o1")));

        mockMvc.perform(get("/orders").param("q", "mug").param("status", "SHIPPED")
                        .header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("o1"));

        verify(orderService).listForBuyer("client-1", "mug", OrderStatus.SHIPPED);
    }

    @Test
    void myOrders_rejectsAStatusThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/orders").param("status", "LOST")
                        .header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'LOST' for parameter 'status'"));

        verifyNoInteractions(orderService);
    }

    @Test
    void myOrders_requiresAToken() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void myOrders_isForbiddenToSellers() throws Exception {
        mockMvc.perform(get("/orders").header("Authorization", bearer("seller-1", "SELLER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }

    @Test
    void ordersReceived_listsTheOrdersOfTheSeller_withFilters() throws Exception {
        when(orderService.listForSeller("seller-1", "book", OrderStatus.PENDING)).thenReturn(List.of(order("o1")));

        mockMvc.perform(get("/orders/seller").param("q", "book").param("status", "PENDING")
                        .header("Authorization", bearer("seller-1", "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("o1"));
    }

    @Test
    void ordersReceived_isForbiddenToClients() throws Exception {
        mockMvc.perform(get("/orders/seller").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }

    @Test
    void ordersReceived_requiresAToken() throws Exception {
        mockMvc.perform(get("/orders/seller")).andExpect(status().isUnauthorized());
    }

    @Test
    void getOrder_isOpenToAnyoneSignedIn_theServiceDecidesWhoMaySee() throws Exception {
        when(orderService.getOrder("o1", "client-1")).thenReturn(order("o1"));
        when(orderService.getOrder("o1", "seller-1")).thenReturn(order("o1"));

        mockMvc.perform(get("/orders/o1").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("o1"));
        mockMvc.perform(get("/orders/o1").header("Authorization", bearer("seller-1", "SELLER")))
                .andExpect(status().isOk());
    }

    @Test
    void getOrder_answersForbidden_forSomeoneElsesOrder() throws Exception {
        when(orderService.getOrder("o1", "client-2"))
                .thenThrow(new NotOrderParticipantException("You can only see orders that you placed or received"));

        mockMvc.perform(get("/orders/o1").header("Authorization", bearer("client-2", "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only see orders that you placed or received"));
    }

    @Test
    void getOrder_answersNotFound_forAMissingOrder() throws Exception {
        when(orderService.getOrder("nope", "client-1")).thenThrow(new OrderNotFoundException("Order with id: nope not found"));

        mockMvc.perform(get("/orders/nope").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_requiresAToken() throws Exception {
        mockMvc.perform(get("/orders/o1")).andExpect(status().isUnauthorized());
    }
}
