package platform.zone01.productservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import platform.zone01.commonsecurity.jwt.JwtService;
import platform.zone01.productservice.config.SecurityConfig;
import platform.zone01.productservice.dto.ProductResponseDTO;
import platform.zone01.productservice.exception.InsufficientStockException;
import platform.zone01.productservice.exception.ProductNotFoundException;
import platform.zone01.productservice.service.ProductService;
import platform.zone01.productservice.service.StockService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductController.class, InternalProductController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class InternalProductApiSecurityTest {

    private static final String RESERVE_BODY = "{\"items\":[{\"productId\":\"p1\",\"quantity\":2}]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private StockService stockService;

    @MockBean
    private ProductService productService;

    private String bearer(String role) {
        return "Bearer " + jwtService.generateToken("user-1", role);
    }

    @Test
    void internalEndpoints_requireAToken() throws Exception {
        mockMvc.perform(post("/internal/products/reserve")
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(stockService);
    }

    @Test
    void internalEndpoints_rejectAForgedToken() throws Exception {
        mockMvc.perform(post("/internal/products/reserve")
                        .header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpoints_areForbiddenToClients() throws Exception {
        mockMvc.perform(post("/internal/products/reserve")
                        .header("Authorization", bearer("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(stockService);
    }

    @Test
    void internalEndpoints_areForbiddenToSellers() throws Exception {
        mockMvc.perform(post("/internal/products/release")
                        .header("Authorization", bearer("SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/products/lookup")
                        .header("Authorization", bearer("SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"p1\"]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(stockService);
    }

    @Test
    void reserve_isAllowedForTheServiceRole_andReturnsTheProducts() throws Exception {
        when(stockService.reserve(any())).thenReturn(List.of(
                new ProductResponseDTO("p1", "Book", "desc", 12.5, 3, "seller-1", "Books", null)));

        mockMvc.perform(post("/internal/products/reserve")
                        .header("Authorization", bearer("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p1"))
                .andExpect(jsonPath("$[0].userId").value("seller-1"))
                .andExpect(jsonPath("$[0].price").value(12.5));

        verify(stockService).reserve(any());
    }

    @Test
    void lookup_isAllowedForTheServiceRole() throws Exception {
        when(stockService.lookup(List.of("p1"))).thenReturn(List.of(
                new ProductResponseDTO("p1", "Book", "desc", 12.5, 3, "seller-1", "Books", null)));

        mockMvc.perform(post("/internal/products/lookup")
                        .header("Authorization", bearer("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"p1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Book"));
    }

    @Test
    void release_answersNoContent() throws Exception {
        mockMvc.perform(post("/internal/products/release")
                        .header("Authorization", bearer("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isNoContent());

        verify(stockService).release(any());
    }

    @Test
    void reserve_answersConflict_whenThereIsNotEnoughStock() throws Exception {
        when(stockService.reserve(any()))
                .thenThrow(new InsufficientStockException("Not enough stock for 'Book': only 1 left"));

        mockMvc.perform(post("/internal/products/reserve")
                        .header("Authorization", bearer("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock for 'Book': only 1 left"));
    }

    @Test
    void reserve_answersNotFound_whenAProductIsGone() throws Exception {
        when(stockService.reserve(any())).thenThrow(new ProductNotFoundException("Product with id: p1 not found"));

        mockMvc.perform(post("/internal/products/reserve")
                        .header("Authorization", bearer("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserve_rejectsAnEmptyItemList() throws Exception {
        mockMvc.perform(post("/internal/products/reserve")
                        .header("Authorization", bearer("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("At least one item is required"));

        verifyNoInteractions(stockService);
    }

    @Test
    void reserve_rejectsAZeroQuantity() throws Exception {
        mockMvc.perform(post("/internal/products/reserve")
                        .header("Authorization", bearer("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"p1\",\"quantity\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").value("Quantity must be greater than 0"));
    }

    @Test
    void lookup_rejectsBlankIds() throws Exception {
        mockMvc.perform(post("/internal/products/lookup")
                        .header("Authorization", bearer("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\" \"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicReads_stayOpenWithoutAToken() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());
    }

    @Test
    void productWrites_stayForSellersOnly() throws Exception {
        String body = "{\"name\":\"Book\",\"category\":\"Books\",\"price\":5,\"quantity\":1}";

        mockMvc.perform(post("/products")
                        .header("Authorization", bearer("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
