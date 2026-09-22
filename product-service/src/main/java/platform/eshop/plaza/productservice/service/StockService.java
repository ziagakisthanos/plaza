package platform.eshop.plaza.productservice.service;

import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import platform.eshop.plaza.productservice.dto.ProductResponseDTO;
import platform.eshop.plaza.productservice.dto.StockItemDTO;
import platform.eshop.plaza.productservice.entity.Product;
import platform.eshop.plaza.productservice.exception.InsufficientStockException;
import platform.eshop.plaza.productservice.exception.ProductNotFoundException;
import platform.eshop.plaza.productservice.repository.ProductRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stock operations used by other services. Each decrement is a single conditional update in the
 * database, so two buyers can never take the same last item. A reservation that fails part-way
 * gives back what it already took.
 */
@Service
public class StockService {

    private static final String QUANTITY = "quantity";

    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;

    public StockService(ProductRepository productRepository, MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<ProductResponseDTO> lookup(List<String> ids) {
        return productRepository.findAllById(ids).stream()
                .map(ProductResponseDTO::from)
                .toList();
    }

    public List<ProductResponseDTO> reserve(List<StockItemDTO> items) {
        Map<String, Integer> wanted = mergeByProduct(items);
        Map<String, Integer> taken = new LinkedHashMap<>();

        try {
            for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
                takeStock(entry.getKey(), entry.getValue());
                taken.put(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException e) {
            giveBack(taken);
            throw e;
        }

        return lookup(new ArrayList<>(wanted.keySet()));
    }

    public void release(List<StockItemDTO> items) {
        giveBack(mergeByProduct(items));
    }

    private void takeStock(String productId, int quantity) {
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(productId).and(QUANTITY).gte(quantity)),
                new Update().inc(QUANTITY, -quantity),
                Product.class);

        if (result.getModifiedCount() == 0) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException("Product with id: " + productId + " not found"));
            throw new InsufficientStockException(
                    "Not enough stock for '" + product.getName() + "': only " + product.getQuantity() + " left");
        }
    }

    private void giveBack(Map<String, Integer> quantities) {
        quantities.forEach((productId, quantity) -> mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(productId)),
                new Update().inc(QUANTITY, quantity),
                Product.class));
    }

    private Map<String, Integer> mergeByProduct(List<StockItemDTO> items) {
        return items.stream().collect(Collectors.toMap(
                StockItemDTO::getProductId,
                StockItemDTO::getQuantity,
                Integer::sum,
                LinkedHashMap::new));
    }
}
