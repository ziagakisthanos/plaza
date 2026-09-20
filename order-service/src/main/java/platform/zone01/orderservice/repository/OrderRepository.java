package platform.zone01.orderservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import platform.zone01.orderservice.entity.Order;

public interface OrderRepository extends MongoRepository<Order, String> {
}
