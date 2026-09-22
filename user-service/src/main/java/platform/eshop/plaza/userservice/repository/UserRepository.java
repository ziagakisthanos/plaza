package platform.eshop.plaza.userservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import platform.eshop.plaza.userservice.entity.User;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
}
