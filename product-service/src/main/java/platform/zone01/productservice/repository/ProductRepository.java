package platform.zone01.productservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import platform.zone01.productservice.entity.Product;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByUserId(String userId);
}
