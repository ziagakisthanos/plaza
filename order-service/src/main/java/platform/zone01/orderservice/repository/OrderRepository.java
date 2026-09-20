package platform.zone01.orderservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import platform.zone01.orderservice.entity.Order;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<Order> findBySellerIdOrderByCreatedAtDesc(String sellerId);
}
