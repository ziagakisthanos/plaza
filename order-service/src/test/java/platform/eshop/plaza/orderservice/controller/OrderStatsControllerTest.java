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
import platform.eshop.plaza.orderservice.dto.ClientStatsDTO;
import platform.eshop.plaza.orderservice.dto.ProductStatDTO;
import platform.eshop.plaza.orderservice.dto.SellerStatsDTO;
import platform.eshop.plaza.orderservice.service.OrderStatsService;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderStatsController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class OrderStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OrderStatsService orderStatsService;

    private String bearer(String userId, String role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }

    @Test
    void clientStats_areReturnedToTheClient_forTheUserInTheToken() throws Exception {
        when(orderStatsService.clientStats("client-1")).thenReturn(new ClientStatsDTO(47.5, 2,
                List.of(new ProductStatDTO("p2", "Pen", 5, 10.0)),
                List.of(new ProductStatDTO("p1", "Book", 3, 37.5))));

        mockMvc.perform(get("/orders/stats/client").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").value(47.5))
                .andExpect(jsonPath("$.orderCount").value(2))
                .andExpect(jsonPath("$.mostBought[0].name").value("Pen"))
                .andExpect(jsonPath("$.mostBought[0].quantity").value(5))
                .andExpect(jsonPath("$.bestProducts[0].name").value("Book"))
                .andExpect(jsonPath("$.bestProducts[0].amount").value(37.5));
    }

    @Test
    void clientStats_areForbiddenToSellers_andNeedAToken() throws Exception {
        mockMvc.perform(get("/orders/stats/client").header("Authorization", bearer("seller-1", "SELLER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/orders/stats/client")).andExpect(status().isUnauthorized());

        verifyNoInteractions(orderStatsService);
    }

    @Test
    void sellerStats_areReturnedToTheSeller_forTheUserInTheToken() throws Exception {
        when(orderStatsService.sellerStats("seller-1")).thenReturn(new SellerStatsDTO(120.0, 4,
                List.of(new ProductStatDTO("p1", "Book", 8, 100.0))));

        mockMvc.perform(get("/orders/stats/seller").header("Authorization", bearer("seller-1", "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarned").value(120.0))
                .andExpect(jsonPath("$.orderCount").value(4))
                .andExpect(jsonPath("$.bestSelling[0].name").value("Book"))
                .andExpect(jsonPath("$.bestSelling[0].quantity").value(8))
                .andExpect(jsonPath("$.bestSelling[0].amount").value(100.0));
    }

    @Test
    void sellerStats_areForbiddenToClients_andNeedAToken() throws Exception {
        mockMvc.perform(get("/orders/stats/seller").header("Authorization", bearer("client-1", "CLIENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/orders/stats/seller")).andExpect(status().isUnauthorized());

        verifyNoInteractions(orderStatsService);
    }
}
