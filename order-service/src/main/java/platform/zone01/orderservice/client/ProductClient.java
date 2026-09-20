package platform.zone01.orderservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import platform.zone01.commonsecurity.jwt.JwtService;
import platform.zone01.orderservice.dto.LookupRequestDTO;
import platform.zone01.orderservice.dto.ProductDTO;
import platform.zone01.orderservice.dto.RemoteErrorDTO;
import platform.zone01.orderservice.dto.StockItemDTO;
import platform.zone01.orderservice.dto.StockRequestDTO;
import platform.zone01.orderservice.exception.InsufficientStockException;
import platform.zone01.orderservice.exception.ProductServiceUnavailableException;
import platform.zone01.orderservice.exception.ProductUnavailableException;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * Calls the internal stock API of the product service. Every request carries a token signed
 * for the SERVICE role, which ordinary users can never obtain.
 */
@Component
public class ProductClient {

    private static final String SERVICE_NAME = "order-service";
    private static final String SERVICE_ROLE = "SERVICE";
    private static final ParameterizedTypeReference<List<ProductDTO>> PRODUCT_LIST = new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public ProductClient(RestClient productRestClient, JwtService jwtService, ObjectMapper objectMapper) {
        this.restClient = productRestClient;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    public List<ProductDTO> lookup(List<String> ids) {
        return call("/internal/products/lookup", new LookupRequestDTO(ids), response -> response.body(PRODUCT_LIST));
    }

    public List<ProductDTO> reserve(List<StockItemDTO> items) {
        return call("/internal/products/reserve", new StockRequestDTO(items), response -> response.body(PRODUCT_LIST));
    }

    public void release(List<StockItemDTO> items) {
        call("/internal/products/release", new StockRequestDTO(items), RestClient.ResponseSpec::toBodilessEntity);
    }

    private <T> T call(String path, Object body, Function<RestClient.ResponseSpec, T> read) {
        try {
            RestClient.ResponseSpec response = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(SERVICE_NAME, SERVICE_ROLE))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, answer) -> {
                        throw new ProductUnavailableException(errorMessage(answer, "Product not found"));
                    })
                    .onStatus(status -> status.value() == 409, (request, answer) -> {
                        throw new InsufficientStockException(errorMessage(answer, "Not enough stock"));
                    })
                    .onStatus(HttpStatusCode::isError, (request, answer) -> {
                        throw new ProductServiceUnavailableException(
                                "Product service answered with status " + answer.getStatusCode().value());
                    });
            return read.apply(response);
        } catch (ResourceAccessException e) {
            throw new ProductServiceUnavailableException("Product service is unreachable");
        }
    }

    private String errorMessage(ClientHttpResponse answer, String fallback) {
        try {
            String message = objectMapper.readValue(answer.getBody(), RemoteErrorDTO.class).getMessage();
            return message == null || message.isBlank() ? fallback : message;
        } catch (IOException e) {
            return fallback;
        }
    }
}
