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
import platform.zone01.orderservice.service.OrderService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class OrderControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OrderService orderService;

    private String client() {
        return "Bearer " + jwtService.generateToken("client-1", "CLIENT");
    }

    @Test
    void unknownUrl_isNotFound_withTheStandardBody() throws Exception {
        mockMvc.perform(get("/orders/a/b/c").header("Authorization", client()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.path").value("/orders/a/b/c"));
    }

    @Test
    void wrongMethod_isMethodNotAllowed_andSaysWhichAreAllowed() throws Exception {
        mockMvc.perform(patch("/orders/o1").header("Authorization", client())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.message").value("Method 'PATCH' is not supported for this URL"));
    }

    @Test
    void wrongContentType_isUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/orders/checkout").header("Authorization", client())
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", containsString("text/plain")));
    }

    @Test
    void anUnexpectedFailure_isAPlainServerError_thatHidesTheDetails() throws Exception {
        when(orderService.listForBuyer("client-1", null, null)).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/orders").header("Authorization", client()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error occurred"));
    }
}
