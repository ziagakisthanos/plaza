package platform.eshop.plaza.mediaservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "media")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Media {
    @Id
    private String id;

    private String imagePath;

    private String contentType;

    @Indexed
    private String productId;

    private String userId;
}
