package platform.zone01.orderservice.service;

import org.springframework.stereotype.Service;
import platform.zone01.orderservice.dto.ClientStatsDTO;
import platform.zone01.orderservice.dto.ProductStatDTO;
import platform.zone01.orderservice.dto.SellerStatsDTO;
import platform.zone01.orderservice.entity.Order;
import platform.zone01.orderservice.entity.OrderItem;
import platform.zone01.orderservice.enums.OrderStatus;
import platform.zone01.orderservice.repository.OrderRepository;
import platform.zone01.orderservice.util.Money;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numbers for the profile pages. Cancelled orders are left out, every other order counts.
 * Products are ranked by what was bought (or sold) and by the money involved; the name shown
 * is the one on the most recent order.
 */
@Service
public class OrderStatsService {

    private static final int TOP_LIMIT = 5;

    private static final Comparator<ProductStatDTO> BY_QUANTITY =
            Comparator.comparingInt(ProductStatDTO::getQuantity).reversed()
                    .thenComparing(Comparator.comparingDouble(ProductStatDTO::getAmount).reversed())
                    .thenComparing(ProductStatDTO::getName);

    private static final Comparator<ProductStatDTO> BY_AMOUNT =
            Comparator.comparingDouble(ProductStatDTO::getAmount).reversed()
                    .thenComparing(Comparator.comparingInt(ProductStatDTO::getQuantity).reversed())
                    .thenComparing(ProductStatDTO::getName);

    private final OrderRepository orderRepository;

    public OrderStatsService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public ClientStatsDTO clientStats(String buyerId) {
        List<Order> orders = counted(orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId));
        List<ProductStatDTO> products = productTotals(orders);
        return new ClientStatsDTO(totalOf(orders), orders.size(), top(products, BY_QUANTITY), top(products, BY_AMOUNT));
    }

    public SellerStatsDTO sellerStats(String sellerId) {
        List<Order> orders = counted(orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerId));
        return new SellerStatsDTO(totalOf(orders), orders.size(), top(productTotals(orders), BY_QUANTITY));
    }

    private List<Order> counted(List<Order> orders) {
        return orders.stream().filter(order -> order.getStatus() != OrderStatus.CANCELLED).toList();
    }

    private double totalOf(List<Order> orders) {
        return Money.round(orders.stream().mapToDouble(Order::getTotal).sum());
    }

    private List<ProductStatDTO> productTotals(List<Order> orders) {
        Map<String, Totals> totals = new LinkedHashMap<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                totals.computeIfAbsent(item.getProductId(), id -> new Totals(id, item.getName()))
                        .add(item.getQuantity(), Money.round(item.getPrice() * item.getQuantity()));
            }
        }
        return totals.values().stream().map(Totals::toStat).toList();
    }

    private List<ProductStatDTO> top(List<ProductStatDTO> products, Comparator<ProductStatDTO> ranking) {
        return products.stream().sorted(ranking).limit(TOP_LIMIT).toList();
    }

    private static final class Totals {
        private final String productId;
        private final String name;
        private int quantity;
        private double amount;

        private Totals(String productId, String name) {
            this.productId = productId;
            this.name = name;
        }

        private void add(int addedQuantity, double addedAmount) {
            quantity += addedQuantity;
            amount += addedAmount;
        }

        private ProductStatDTO toStat() {
            return new ProductStatDTO(productId, name, quantity, Money.round(amount));
        }
    }
}
