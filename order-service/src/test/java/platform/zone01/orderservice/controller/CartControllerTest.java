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
import platform.zone01.orderservice.dto.CartItemResponseDTO;
import platform.zone01.orderservice.dto.CartResponseDTO;
import platform.zone01.orderservice.exception.CartConflictException;
import platform.zone01.orderservice.exception.CartItemNotFoundException;
import platform.zone01.orderservice.exception.InsufficientStockException;
import platform.zone01.orderservice.exception.ProductServiceUnavailableException;
import platform.zone01.orderservice.exception.ProductUnavailableException;
import platform.zone01.orderservice.service.CartService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class CartControllerTest {

    private static final CartResponseDTO CART = new CartResponseDTO(
            List.of(new CartItemResponseDTO("p1", "Book", 12.5, 2, 10, 25.0)), 2, 25.0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private CartService cartService;

    private String bearer(String userId, String role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }

    @Test
    void getCart_returnsTheCartOfTheAuthenticatedClient() throws Exception {
        when(cartService.getCart("client-1")).thenReturn(CART);

        mockMvc.perform(get("/cart").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value("p1"))
                .andExpect(jsonPath("$.items[0].name").value("Book"))
                .andExpect(jsonPath("$.items[0].availableStock").value(10))
                .andExpect(jsonPath("$.items[0].lineTotal").value(25.0))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.total").value(25.0));
    }

    @Test
    void cart_requiresAToken() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(cartService);
    }

    @Test
    void cart_isForbiddenToSellers_onEveryEndpoint() throws Exception {
        String seller = bearer("seller-1", "SELLER");
        String body = "{\"productId\":\"p1\",\"quantity\":1}";

        mockMvc.perform(get("/cart").header("Authorization", seller)).andExpect(status().isForbidden());
        mockMvc.perform(post("/cart/items").header("Authorization", seller)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/cart/items/p1").header("Authorization", seller)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/cart/items/p1").header("Authorization", seller)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/cart").header("Authorization", seller)).andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void addItem_usesTheIdentityFromTheToken_notFromTheBody() throws Exception {
        when(cartService.addItem("client-1", "p1", 2)).thenReturn(CART);

        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"quantity\":2,\"userId\":\"someone-else\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2));

        verify(cartService).addItem("client-1", "p1", 2);
    }

    @Test
    void addItem_rejectsAnInvalidBody() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\" \",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.productId").value("Product id is required"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity must be at least 1"));

        verifyNoInteractions(cartService);
    }

    @Test
    void addItem_rejectsAMissingQuantity() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity is required"));
    }

    @Test
    void addItem_rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void addItem_rejectsAQuantityThatIsNotANumber() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"quantity\":\"many\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_answersConflict_whenThereIsNotEnoughStock() throws Exception {
        when(cartService.addItem("client-1", "p1", 9))
                .thenThrow(new InsufficientStockException("Not enough stock for 'Book': only 3 left"));

        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"quantity\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock for 'Book': only 3 left"));
    }

    @Test
    void addItem_answersConflict_whenTheCartKeepsChangingAtTheSameTime() throws Exception {
        when(cartService.addItem("client-1", "p1", 1))
                .thenThrow(new CartConflictException("Your cart is being changed elsewhere, please try again"));

        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Your cart is being changed elsewhere, please try again"));
    }

    @Test
    void addItem_answersNotFound_whenTheProductDoesNotExist() throws Exception {
        when(cartService.addItem("client-1", "nope", 1))
                .thenThrow(new ProductUnavailableException("Product with id: nope not found"));

        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"nope\",\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product with id: nope not found"));
    }

    @Test
    void addItem_answersServiceUnavailable_withAFriendlyMessage_whenTheProductServiceIsDown() throws Exception {
        when(cartService.addItem("client-1", "p1", 1))
                .thenThrow(new ProductServiceUnavailableException("Product service is unreachable"));

        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Products are temporarily unavailable, please try again"));
    }

    @Test
    void updateItem_setsTheQuantityForThatProduct() throws Exception {
        when(cartService.updateItem("client-1", "p1", 4)).thenReturn(CART);

        mockMvc.perform(put("/cart/items/p1")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk());

        verify(cartService).updateItem("client-1", "p1", 4);
    }

    @Test
    void updateItem_rejectsAZeroQuantity() throws Exception {
        mockMvc.perform(put("/cart/items/p1")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity must be at least 1"));
    }

    @Test
    void updateItem_answersNotFound_whenTheProductIsNotInTheCart() throws Exception {
        when(cartService.updateItem("client-1", "p9", 1))
                .thenThrow(new CartItemNotFoundException("Product with id: p9 is not in your cart"));

        mockMvc.perform(put("/cart/items/p9")
                        .header("Authorization", bearer("client-1", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product with id: p9 is not in your cart"));
    }

    @Test
    void removeItem_returnsTheRemainingCart() throws Exception {
        when(cartService.removeItem("client-1", "p1")).thenReturn(CART);

        mockMvc.perform(delete("/cart/items/p1").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25.0));
    }

    @Test
    void clear_answersNoContent() throws Exception {
        mockMvc.perform(delete("/cart").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isNoContent());

        verify(cartService).clear("client-1");
    }
}
