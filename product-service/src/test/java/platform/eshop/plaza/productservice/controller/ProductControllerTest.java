package platform.eshop.plaza.productservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import platform.eshop.plaza.productservice.dto.ProductResponseDTO;
import platform.eshop.plaza.productservice.dto.ProductSearchCriteria;
import platform.eshop.plaza.productservice.exception.GlobalExceptionHandler;
import platform.eshop.plaza.productservice.service.ProductService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void search_bindsAllQueryParametersIntoCriteria() throws Exception {
        when(productService.searchProducts(any())).thenReturn(List.of(
                new ProductResponseDTO("p1", "Laptop", "desc", 999.0, 3, "s1", "Electronics", Instant.now())));

        mockMvc.perform(get("/products")
                        .param("q", "lap")
                        .param("category", "Electronics")
                        .param("minPrice", "10")
                        .param("maxPrice", "1000")
                        .param("inStock", "true")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Laptop"))
                .andExpect(jsonPath("$[0].category").value("Electronics"));

        ArgumentCaptor<ProductSearchCriteria> captor = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(productService).searchProducts(captor.capture());
        ProductSearchCriteria criteria = captor.getValue();
        assertThat(criteria.getQ()).isEqualTo("lap");
        assertThat(criteria.getCategory()).isEqualTo("Electronics");
        assertThat(criteria.getMinPrice()).isEqualTo(10.0);
        assertThat(criteria.getMaxPrice()).isEqualTo(1000.0);
        assertThat(criteria.getInStock()).isTrue();
        assertThat(criteria.getSort()).isEqualTo("price_asc");
    }

    @Test
    void search_withoutParameters_isAllowed() throws Exception {
        when(productService.searchProducts(any())).thenReturn(List.of());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void search_rejectsMinPriceGreaterThanMaxPrice() throws Exception {
        mockMvc.perform(get("/products").param("minPrice", "50").param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.priceRangeValid")
                        .value("Minimum price cannot be greater than maximum price"));

        verify(productService, never()).searchProducts(any());
    }

    @Test
    void search_rejectsNegativePrice() throws Exception {
        mockMvc.perform(get("/products").param("minPrice", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minPrice").value("Minimum price cannot be negative"));
    }

    @Test
    void search_rejectsUnknownSort() throws Exception {
        mockMvc.perform(get("/products").param("sort", "cheapest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sort").value("Sort must be newest, price_asc or price_desc"));
    }

    @Test
    void categories_returnsTheList() throws Exception {
        when(productService.getCategories()).thenReturn(List.of("Books", "Toys"));

        mockMvc.perform(get("/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Books"))
                .andExpect(jsonPath("$[1]").value("Toys"));
    }

    @Test
    void getProduct_stillResolvesById() throws Exception {
        when(productService.getProductById("abc")).thenReturn(
                new ProductResponseDTO("abc", "Laptop", "desc", 999.0, 3, "s1", "Electronics", Instant.now()));

        mockMvc.perform(get("/products/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc"));
    }
}
