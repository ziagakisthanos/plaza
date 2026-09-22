package platform.eshop.plaza.productservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import platform.eshop.plaza.productservice.dto.ProductRequestDTO;
import platform.eshop.plaza.productservice.dto.ProductResponseDTO;
import platform.eshop.plaza.productservice.dto.ProductSearchCriteria;
import platform.eshop.plaza.productservice.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("")
    public ResponseEntity<List<ProductResponseDTO>> searchProducts(@Valid @ModelAttribute ProductSearchCriteria criteria) {
        List<ProductResponseDTO> products = productService.searchProducts(criteria);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(productService.getCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProduct(@PathVariable String id) {
        ProductResponseDTO product = productService.getProductById(id);
        return ResponseEntity.ok(product);

    }

    @PostMapping("")
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestBody ProductRequestDTO request,
            @AuthenticationPrincipal String userId) {
        ProductResponseDTO created = productService.createProduct(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @Valid @RequestBody ProductRequestDTO request,
            @PathVariable String id,
            @AuthenticationPrincipal String userId) {
        ProductResponseDTO updated = productService.updateProduct(request, id, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> deleteProduct(
            @PathVariable String id,
            @AuthenticationPrincipal String userId) {
        productService.deleteProduct(id, userId);
        return ResponseEntity.noContent().build();
    }
}
