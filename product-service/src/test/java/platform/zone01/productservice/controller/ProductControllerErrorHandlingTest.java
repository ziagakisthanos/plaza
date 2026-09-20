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
import platform.zone01.productservice.service.ProductService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-1234",
        "jwt.expiration=3600000"
})
class ProductControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ProductService productService;

    private String seller() {
        return "Bearer " + jwtService.generateToken("seller-1", "SELLER");
    }

    @Test
    void unknownUrl_isNotFound_withTheStandardBody() throws Exception {
        mockMvc.perform(get("/products/a/b/c"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.path").value("/products/a/b/c"));
    }

    @Test
    void malformedJson_isBadRequest() throws Exception {
        mockMvc.perform(post("/products").header("Authorization", seller())
                        .contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void wrongMethod_isMethodNotAllowed_andSaysWhichAreAllowed() throws Exception {
        mockMvc.perform(patch("/products/p1").header("Authorization", seller())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.message").value("Method 'PATCH' is not supported for this URL"));
    }

    @Test
    void wrongContentType_isUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/products").header("Authorization", seller())
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", containsString("text/plain")));
    }

    @Test
    void aFilterThatIsNotANumber_isBadRequest_withTheStandardBody() throws Exception {
        mockMvc.perform(get("/products").param("minPrice", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.minPrice").exists());
    }

    @Test
    void anUnexpectedFailure_isAPlainServerError_thatHidesTheDetails() throws Exception {
        when(productService.searchProducts(any())).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error occurred"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(containsString("hunter2"))));
    }
}
