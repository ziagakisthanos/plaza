package platform.zone01.orderservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import platform.zone01.commonsecurity.jwt.JwtService;
import platform.zone01.orderservice.config.SecurityConfig;
import platform.zone01.orderservice.dto.OrderItemResponseDTO;
import platform.zone01.orderservice.dto.OrderResponseDTO;
import platform.zone01.orderservice.enums.OrderStatus;
import platform.zone01.orderservice.enums.PaymentMethod;
import platform.zone01.orderservice.exception.InsufficientStockException;
import platform.zone01.orderservice.exception.InvalidOrderStateException;
import platform.zone01.orderservice.exception.NotOrderParticipantException;
import platform.zone01.orderservice.exception.OrderNotFoundException;
import platform.zone01.orderservice.service.OrderService;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class OrderControllerManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OrderService orderService;

    private String bearer(String userId, String role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }

    private static OrderResponseDTO order(OrderStatus status) {
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        return new OrderResponseDTO("o1", "client-1", "seller-1",
                List.of(new OrderItemResponseDTO("p1", "Book", 12.5, 2, 25.0)),
                25.0, status, PaymentMethod.PAY_ON_DELIVERY, "12 Main Street", now, now);
    }

    @Test
    void updateStatus_isAllowedForSellers_andReturnsTheOrder() throws Exception {
        when(orderService.updateStatus("o1", "seller-1", OrderStatus.CONFIRMED)).thenReturn(order(OrderStatus.CONFIRMED));

        mockMvc.perform(put("/orders/o1/status")
                        .header("Authorization", bearer("seller-1", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void updateStatus_isForbiddenToClients_andNeedsAToken() throws Exception {
        mockMvc.perform(put("/orders/o1/status")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/orders/o1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void updateStatus_rejectsAMissingStatus() throws Exception {
        mockMvc.perform(put("/orders/o1/status")
                        .header("Authorization", bearer("seller-1", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("Please choose a status"));
    }

    @Test
    void updateStatus_rejectsAStatusThatDoesNotExist() throws Exception {
        mockMvc.perform(put("/orders/o1/status")
                        .header("Authorization", bearer("seller-1", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void updateStatus_answersConflict_forAnImpossibleStep() throws Exception {
        when(orderService.updateStatus("o1", "seller-1", OrderStatus.DELIVERED))
                .thenThrow(new InvalidOrderStateException("An order that is PENDING cannot be changed to DELIVERED"));

        mockMvc.perform(put("/orders/o1/status")
                        .header("Authorization", bearer("seller-1", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An order that is PENDING cannot be changed to DELIVERED"));
    }

    @Test
    void updateStatus_answersForbidden_forAnotherSellersOrder() throws Exception {
        when(orderService.updateStatus("o1", "seller-2", OrderStatus.CONFIRMED))
                .thenThrow(new NotOrderParticipantException("Only the seller of an order can change its status"));

        mockMvc.perform(put("/orders/o1/status")
                        .header("Authorization", bearer("seller-2", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_answersConflict_whenTheOrderWasChangedAtTheSameTime() throws Exception {
        when(orderService.updateStatus("o1", "seller-1", OrderStatus.CONFIRMED))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        mockMvc.perform(put("/orders/o1/status")
                        .header("Authorization", bearer("seller-1", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The order was changed at the same time, please reload and try again"));
    }

    @Test
    void cancel_isAllowedForBothClientsAndSellers() throws Exception {
        when(orderService.cancel("o1", "client-1")).thenReturn(order(OrderStatus.CANCELLED));
        when(orderService.cancel("o1", "seller-1")).thenReturn(order(OrderStatus.CANCELLED));

        mockMvc.perform(put("/orders/o1/cancel").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(put("/orders/o1/cancel").header("Authorization", bearer("seller-1", "SELLER")))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_needsAToken() throws Exception {
        mockMvc.perform(put("/orders/o1/cancel")).andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void cancel_answersConflict_forAnOrderThatIsTooFarAlong() throws Exception {
        when(orderService.cancel("o1", "client-1"))
                .thenThrow(new InvalidOrderStateException("Only pending or confirmed orders can be cancelled"));

        mockMvc.perform(put("/orders/o1/cancel").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only pending or confirmed orders can be cancelled"));
    }

    @Test
    void cancel_answersNotFound_forAMissingOrder() throws Exception {
        when(orderService.cancel("o1", "client-1")).thenThrow(new OrderNotFoundException("Order with id: o1 not found"));

        mockMvc.perform(put("/orders/o1/cancel").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void remove_answersNoContent_forClients() throws Exception {
        mockMvc.perform(delete("/orders/o1").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isNoContent());

        verify(orderService).remove("o1", "client-1");
    }

    @Test
    void remove_isForbiddenToSellers_andNeedsAToken() throws Exception {
        mockMvc.perform(delete("/orders/o1").header("Authorization", bearer("seller-1", "SELLER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/orders/o1")).andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void remove_answersConflict_forAnOrderThatIsNotCancelled() throws Exception {
        doThrow(new InvalidOrderStateException("Only cancelled orders can be removed"))
                .when(orderService).remove("o1", "client-1");

        mockMvc.perform(delete("/orders/o1").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isConflict());
    }

    @Test
    void redo_answersCreated_forClients() throws Exception {
        when(orderService.redo("o1", "client-1")).thenReturn(order(OrderStatus.PENDING));

        mockMvc.perform(post("/orders/o1/redo").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void redo_isForbiddenToSellers_andNeedsAToken() throws Exception {
        mockMvc.perform(post("/orders/o1/redo").header("Authorization", bearer("seller-1", "SELLER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/orders/o1/redo")).andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void redo_answersConflict_whenTheStockIsGone() throws Exception {
        when(orderService.redo("o1", "client-1"))
                .thenThrow(new InsufficientStockException("Not enough stock for 'Book': only 0 left"));

        mockMvc.perform(post("/orders/o1/redo").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock for 'Book': only 0 left"));
    }
}
