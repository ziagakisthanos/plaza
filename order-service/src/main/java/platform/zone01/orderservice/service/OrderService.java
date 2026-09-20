package platform.zone01.orderservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import platform.zone01.orderservice.client.ProductClient;
import platform.zone01.orderservice.dto.CheckoutRequestDTO;
import platform.zone01.orderservice.dto.OrderResponseDTO;
import platform.zone01.orderservice.dto.ProductDTO;
import platform.zone01.orderservice.dto.StockItemDTO;
import platform.zone01.orderservice.entity.Cart;
import platform.zone01.orderservice.entity.Order;
import platform.zone01.orderservice.entity.OrderItem;
import platform.zone01.orderservice.enums.OrderStatus;
import platform.zone01.orderservice.enums.PaymentMethod;
import platform.zone01.orderservice.exception.CartEmptyException;
import platform.zone01.orderservice.exception.ProductUnavailableException;
import platform.zone01.orderservice.repository.OrderRepository;
import platform.zone01.orderservice.util.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns carts into orders. Checkout takes the cart out of the database in one atomic step, so
 * pressing the button twice cannot order the same items twice. Stock is reserved in the product
 * service, one order is created per seller with the prices of that moment, and if anything goes
 * wrong the stock is released and the cart is put back.
 */
@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final MongoTemplate mongoTemplate;

    public OrderService(OrderRepository orderRepository, ProductClient productClient, MongoTemplate mongoTemplate) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.mongoTemplate = mongoTemplate;
    }

    public List<OrderResponseDTO> checkout(String buyerId, CheckoutRequestDTO request) {
        Cart cart = claimCart(buyerId);
        try {
            List<StockItemDTO> items = cart.getItems().stream()
                    .map(item -> new StockItemDTO(item.getProductId(), item.getQuantity()))
                    .toList();
            return placeOrders(buyerId, items, request.getDeliveryAddress().trim(), request.getPaymentMethod()).stream()
                    .map(OrderResponseDTO::from)
                    .toList();
        } catch (RuntimeException e) {
            restoreCart(cart);
            throw e;
        }
    }

    private Cart claimCart(String buyerId) {
        Cart cart = mongoTemplate.findAndRemove(Query.query(Criteria.where("userId").is(buyerId)), Cart.class);
        if (cart == null || cart.getItems().isEmpty()) {
            throw new CartEmptyException("Your cart is empty");
        }
        return cart;
    }

    private void restoreCart(Cart cart) {
        try {
            mongoTemplate.insert(cart);
        } catch (DuplicateKeyException e) {
            log.warn("A new cart already exists for user {}, the cart of the failed checkout was not restored", cart.getUserId());
        }
    }

    private List<Order> placeOrders(String buyerId, List<StockItemDTO> items, String deliveryAddress, PaymentMethod paymentMethod) {
        Map<String, ProductDTO> products = productClient.reserve(items).stream()
                .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));
        try {
            return orderRepository.saveAll(buildOrders(buyerId, items, products, deliveryAddress, paymentMethod));
        } catch (RuntimeException e) {
            releaseQuietly(items);
            throw e;
        }
    }

    private List<Order> buildOrders(String buyerId, List<StockItemDTO> items, Map<String, ProductDTO> products,
                                    String deliveryAddress, PaymentMethod paymentMethod) {
        Map<String, List<OrderItem>> itemsBySeller = new LinkedHashMap<>();
        for (StockItemDTO item : items) {
            ProductDTO product = products.get(item.getProductId());
            if (product == null) {
                throw new ProductUnavailableException("Product with id: " + item.getProductId() + " not found");
            }
            itemsBySeller.computeIfAbsent(product.getUserId(), seller -> new ArrayList<>())
                    .add(new OrderItem(product.getId(), product.getName(), product.getPrice(), item.getQuantity()));
        }

        Instant now = Instant.now();
        return itemsBySeller.entrySet().stream()
                .map(entry -> new Order(null, buyerId, entry.getKey(), entry.getValue(), totalOf(entry.getValue()),
                        OrderStatus.PENDING, paymentMethod, deliveryAddress, now, now, null))
                .toList();
    }

    private double totalOf(List<OrderItem> items) {
        return Money.round(items.stream()
                .mapToDouble(item -> Money.round(item.getPrice() * item.getQuantity()))
                .sum());
    }

    private void releaseQuietly(List<StockItemDTO> items) {
        try {
            productClient.release(items);
        } catch (RuntimeException e) {
            log.error("Could not release the reserved stock after a failed order, it must be released by hand: {}", e.getMessage());
        }
    }
}
