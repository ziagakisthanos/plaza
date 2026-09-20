package platform.zone01.orderservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import platform.zone01.commonsecurity.jwt.JwtService;
import platform.zone01.orderservice.dto.ProductDTO;
import platform.zone01.orderservice.dto.StockItemDTO;
import platform.zone01.orderservice.exception.InsufficientStockException;
import platform.zone01.orderservice.exception.ProductServiceUnavailableException;
import platform.zone01.orderservice.exception.ProductUnavailableException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductClientTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";
    private static final String PRODUCTS_JSON = """
            [{"id":"p1","name":"Book","description":"ignored","price":12.5,"quantity":3,
              "userId":"seller-1","category":"Books","createdAt":null}]""";

    private JwtService jwtService;
    private MockRestServiceServer server;
    private ProductClient client;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl("http://product-service").build();
        client = new ProductClient(restClient, jwtService, new ObjectMapper());
    }

    @Test
    void lookup_postsTheIds_andMapsTheProducts() {
        server.expect(requestTo("http://product-service/internal/products/lookup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"ids\":[\"p1\",\"p2\"]}", true))
                .andRespond(withSuccess(PRODUCTS_JSON, MediaType.APPLICATION_JSON));

        List<ProductDTO> products = client.lookup(List.of("p1", "p2"));

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getId()).isEqualTo("p1");
        assertThat(products.get(0).getName()).isEqualTo("Book");
        assertThat(products.get(0).getPrice()).isEqualTo(12.5);
        assertThat(products.get(0).getQuantity()).isEqualTo(3);
        assertThat(products.get(0).getUserId()).isEqualTo("seller-1");
        server.verify();
    }

    @Test
    void reserve_postsTheItems_andReturnsTheReservedProducts() {
        server.expect(requestTo("http://product-service/internal/products/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"items\":[{\"productId\":\"p1\",\"quantity\":2}]}", true))
                .andRespond(withSuccess(PRODUCTS_JSON, MediaType.APPLICATION_JSON));

        List<ProductDTO> products = client.reserve(List.of(new StockItemDTO("p1", 2)));

        assertThat(products).extracting(ProductDTO::getId).containsExactly("p1");
        server.verify();
    }

    @Test
    void release_postsTheItems_andAcceptsNoContent() {
        server.expect(requestTo("http://product-service/internal/products/release"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"items\":[{\"productId\":\"p1\",\"quantity\":2}]}", true))
                .andRespond(withNoContent());

        client.release(List.of(new StockItemDTO("p1", 2)));

        server.verify();
    }

    @Test
    void everyRequest_carriesATokenForTheServiceRole() {
        server.expect(requestTo("http://product-service/internal/products/lookup"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, startsWith("Bearer ")))
                .andExpect(request -> {
                    String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION).substring(7);
                    assertThat(jwtService.isTokenValid(token)).isTrue();
                    assertThat(jwtService.extractRole(token)).isEqualTo("SERVICE");
                    assertThat(jwtService.extractUserId(token)).isEqualTo("order-service");
                })
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.lookup(List.of("p1"))).isEmpty();
        server.verify();
    }

    @Test
    void reserve_turnsAConflictIntoAnInsufficientStockError_withTheProductServiceMessage() {
        server.expect(requestTo("http://product-service/internal/products/reserve"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"Not enough stock for 'Book': only 1 left\"}"));

        assertThatThrownBy(() -> client.reserve(List.of(new StockItemDTO("p1", 5))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Book': only 1 left");
    }

    @Test
    void reserve_turnsANotFoundIntoAProductUnavailableError() {
        server.expect(requestTo("http://product-service/internal/products/reserve"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Product with id: p1 not found\"}"));

        assertThatThrownBy(() -> client.reserve(List.of(new StockItemDTO("p1", 1))))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessage("Product with id: p1 not found");
    }

    @Test
    void errors_fallBackToADefaultMessage_whenTheBodyIsNotJson() {
        server.expect(requestTo("http://product-service/internal/products/reserve"))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("oops"));

        assertThatThrownBy(() -> client.reserve(List.of(new StockItemDTO("p1", 1))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock");
    }

    @Test
    void errors_fallBackToADefaultMessage_whenTheBodyHasNoMessage() {
        server.expect(requestTo("http://product-service/internal/products/reserve"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> client.reserve(List.of(new StockItemDTO("p1", 1))))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessage("Product not found");
    }

    @Test
    void aServerError_meansTheProductServiceIsUnavailable() {
        server.expect(requestTo("http://product-service/internal/products/lookup"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.lookup(List.of("p1")))
                .isInstanceOf(ProductServiceUnavailableException.class)
                .hasMessage("Product service answered with status 500");
    }

    @Test
    void aRejectedServiceToken_isReportedAsUnavailable_notAsAMissingProduct() {
        server.expect(requestTo("http://product-service/internal/products/lookup"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.lookup(List.of("p1")))
                .isInstanceOf(ProductServiceUnavailableException.class)
                .hasMessage("Product service answered with status 403");
    }

    @Test
    void aConnectionFailure_meansTheProductServiceIsUnavailable() {
        server.expect(requestTo("http://product-service/internal/products/release"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.release(List.of(new StockItemDTO("p1", 1))))
                .isInstanceOf(ProductServiceUnavailableException.class)
                .hasMessage("Product service is unreachable");
    }
}
