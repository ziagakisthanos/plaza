package platform.zone01.mediaservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import platform.zone01.mediaservice.service.MediaService;

@Component
public class ProductDeletedListener {
    private final MediaService mediaService;

    public ProductDeletedListener(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @KafkaListener(topics = "product-deleted", groupId = "media-service")
    public void handleProductDeleted(String productId) {
        mediaService.deleteImagesEventTrigger(productId);
    }
}
