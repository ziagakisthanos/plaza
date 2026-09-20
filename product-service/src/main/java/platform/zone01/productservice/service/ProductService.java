package platform.zone01.productservice.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import platform.zone01.productservice.dto.ProductRequestDTO;
import platform.zone01.productservice.dto.ProductResponseDTO;
import platform.zone01.productservice.entity.Product;
import platform.zone01.productservice.exception.NotProductOwnerException;
import platform.zone01.productservice.exception.ProductNotFoundException;
import platform.zone01.productservice.repository.ProductRepository;

import java.time.Instant;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String PRODUCT_DELETED = "product-deleted";

    public ProductService(ProductRepository productRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.productRepository = productRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public List<ProductResponseDTO> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public ProductResponseDTO getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with id: " + id + " not found"));
        return toResponseDTO(product);
    }

    public ProductResponseDTO createProduct(ProductRequestDTO requestDto, String userId) {
        Product product = new Product();
        product.setName(requestDto.getName());
        product.setDescription(requestDto.getDescription());
        product.setPrice(requestDto.getPrice());
        product.setQuantity(requestDto.getQuantity());
        product.setUserId(userId);
        product.setCategory(requestDto.getCategory().trim());
        product.setCreatedAt(Instant.now());

        Product saved = productRepository.save(product);
        return toResponseDTO(saved);
    }

    public ProductResponseDTO updateProduct(ProductRequestDTO requestDto,String id, String userId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with id: " + id + " not found"));

        if (!product.getUserId().equals(userId)) {
           throw new NotProductOwnerException("You can only edit products that belong to you.");
        }

        product.setName(requestDto.getName());
        product.setDescription(requestDto.getDescription());
        product.setPrice(requestDto.getPrice());
        product.setQuantity(requestDto.getQuantity());
        product.setCategory(requestDto.getCategory().trim());

        Product updated = productRepository.save(product);
        return toResponseDTO(updated);
    }

    public void deleteProduct(String id, String userId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with id: " + id + " not found"));

        if (!product.getUserId().equals(userId)) {
            throw new NotProductOwnerException("You can only delete products that belong to you.");
        }

        productRepository.delete(product);

        kafkaTemplate.send(PRODUCT_DELETED, id);
    }

    private ProductResponseDTO toResponseDTO(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getUserId(),
                product.getCategory(),
                product.getCreatedAt());
    }
}
