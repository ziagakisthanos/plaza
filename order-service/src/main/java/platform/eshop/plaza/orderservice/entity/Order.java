package platform.eshop.plaza.orderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import platform.eshop.plaza.orderservice.enums.OrderStatus;
import platform.eshop.plaza.orderservice.enums.PaymentMethod;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "orders")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Order {
    @Id
    private String id;

    @Indexed
    private String buyerId;

    @Indexed
    private String sellerId;

    private List<OrderItem> items = new ArrayList<>();

    private double total;

    private OrderStatus status;

    private PaymentMethod paymentMethod;

    private String deliveryAddress;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
