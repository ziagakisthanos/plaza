package platform.zone01.productservice.service;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import platform.zone01.productservice.dto.ProductRequestDTO;
import platform.zone01.productservice.dto.ProductResponseDTO;
import platform.zone01.productservice.dto.ProductSearchCriteria;
import platform.zone01.productservice.entity.Product;
import platform.zone01.productservice.exception.NotProductOwnerException;
import platform.zone01.productservice.exception.ProductNotFoundException;
import platform.zone01.productservice.repository.ProductRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MongoTemplate mongoTemplate;

    private static final String PRODUCT_DELETED = "product-deleted";
    private static final String PRICE = "price";
    private static final String CREATED_AT = "createdAt";
    private static final String CATEGORY = "category";

    public ProductService(ProductRepository productRepository,
                          KafkaTemplate<String, String> kafkaTemplate,
                          MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.mongoTemplate = mongoTemplate;
    }

    public List<ProductResponseDTO> searchProducts(ProductSearchCriteria criteria) {
        Query query = buildQuery(criteria);
        return mongoTemplate.find(query, Product.class).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public List<String> getCategories() {
        return mongoTemplate.findDistinct(new Query(), CATEGORY, Product.class, String.class).stream()
                .filter(category -> category != null && !category.isBlank())
                .sorted()
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

    private Query buildQuery(ProductSearchCriteria criteria) {
        List<Criteria> filters = new ArrayList<>();

        if (criteria.getQ() != null && !criteria.getQ().isBlank()) {
            String text = Pattern.quote(criteria.getQ().trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("name").regex(text, "i"),
                    Criteria.where("description").regex(text, "i")));
        }

        if (criteria.getCategory() != null && !criteria.getCategory().isBlank()) {
            filters.add(Criteria.where(CATEGORY).is(criteria.getCategory().trim()));
        }

        if (criteria.getMinPrice() != null || criteria.getMaxPrice() != null) {
            Criteria price = Criteria.where(PRICE);
            if (criteria.getMinPrice() != null) {
                price.gte(criteria.getMinPrice());
            }
            if (criteria.getMaxPrice() != null) {
                price.lte(criteria.getMaxPrice());
            }
            filters.add(price);
        }

        if (Boolean.TRUE.equals(criteria.getInStock())) {
            filters.add(Criteria.where("quantity").gt(0));
        }

        Query query = filters.isEmpty()
                ? new Query()
                : new Query(new Criteria().andOperator(filters));
        return query.with(sortFor(criteria.getSort()));
    }

    private Sort sortFor(String sort) {
        return switch (sort == null ? "newest" : sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, PRICE);
            case "price_desc" -> Sort.by(Sort.Direction.DESC, PRICE);
            default -> Sort.by(Sort.Direction.DESC, CREATED_AT);
        };
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
