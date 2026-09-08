package platform.zone01.productservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import platform.zone01.productservice.dto.ProductRequestDTO;
import platform.zone01.productservice.dto.ProductResponseDTO;
import platform.zone01.productservice.entity.Product;
import platform.zone01.productservice.exception.NotProductOwnerException;
import platform.zone01.productservice.exception.ProductNotFoundException;
import platform.zone01.productservice.repository.ProductRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)   // enables Mockito's @Mock
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProduct_setsOwnerFromCallerId_notFromRequest() {
        ProductRequestDTO request = new ProductRequestDTO("Laptop", "A good laptop", 999.0, 5);
        String callerId = "seller-123";
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product p = invocation.getArgument(0);
                    p.setId("generated-id");
                    return p;
                });

        ProductResponseDTO result = productService.createProduct(request, callerId);

        assertThat(result.getUserId()).isEqualTo("seller-123");
        assertThat(result.getName()).isEqualTo("Laptop");
        assertThat(result.getId()).isEqualTo("generated-id");
    }

    @Test
    void updateProduct_throwsForbidden_whenCallerIsNotOwner() {
        Product existing = new Product("prod-1", "Laptop", "desc", 999.0, 5, "owner-A");
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(existing));

        ProductRequestDTO request = new ProductRequestDTO("Hacked", "desc", 1.0, 1);

        assertThatThrownBy(() ->
                productService.updateProduct(request, "prod-1", "attacker-B"))
                .isInstanceOf(NotProductOwnerException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_succeeds_whenCallerIsOwner() {
        Product existing = new Product("prod-1", "Laptop", "desc", 999.0, 5, "owner-A");
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(existing));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProductRequestDTO request = new ProductRequestDTO("Updated name", "new desc", 1.0, 1);


        ProductResponseDTO result = productService.updateProduct(request, "prod-1", "owner-A");

        assertThat(result.getName()).isEqualTo("Updated name");
        assertThat(result.getPrice()).isEqualTo(1.0);
        assertThat(result.getDescription()).isEqualTo("new desc");
        assertThat(result.getQuantity()).isEqualTo(1);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void deleteProduct_throwsForbidden_whenNotOwner() {
        Product existing = new Product("prod-1", "Laptop", "desc", 999.0, 5, "owner-A");
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                productService.deleteProduct("prod-1", "attacker-B"))
                .isInstanceOf(NotProductOwnerException.class);

        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void getProductById_throwsNotFound_whenMissing() {
        when(productRepository.findById("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.getProductById("missing-id"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateProduct_throwsNotFound_whenProductMissing() {
        when(productRepository.findById("missing-id")).thenReturn(Optional.empty());

        ProductRequestDTO request = new ProductRequestDTO("Laptop", "desc", 1.0, 1);

        assertThatThrownBy(() ->
                productService.updateProduct(request, "missing-id", "any-caller"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any(Product.class));

    }
}