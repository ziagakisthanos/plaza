package platform.zone01.mediaservice.repository;


import org.springframework.data.mongodb.repository.MongoRepository;
import platform.zone01.mediaservice.entity.Media;

import java.util.List;

public interface MediaRepository extends MongoRepository<Media, String> {
    List<Media> findByProductId(String productId);
}
