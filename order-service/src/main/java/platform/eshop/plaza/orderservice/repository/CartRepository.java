package platform.eshop.plaza.orderservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import platform.eshop.plaza.orderservice.entity.Cart;

import java.util.Optional;

public interface CartRepository extends MongoRepository<Cart, String> {
    Optional<Cart> findByUserId(String userId);

    void deleteByUserId(String userId);
}
