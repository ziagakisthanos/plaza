package platform.zone01.orderservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import platform.zone01.orderservice.dto.ClientStatsDTO;
import platform.zone01.orderservice.dto.ProductStatDTO;
import platform.zone01.orderservice.dto.SellerStatsDTO;
import platform.zone01.orderservice.entity.Order;
import platform.zone01.orderservice.entity.OrderItem;
import platform.zone01.orderservice.enums.OrderStatus;
import platform.zone01.orderservice.enums.PaymentMethod;
import platform.zone01.orderservice.repository.OrderRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatsServiceTest {

    private static final String USER = "user-1";

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderStatsService statsService;

    private static OrderItem item(String productId, String name, double price, int quantity) {
        return new OrderItem(productId, name, price, quantity);
    }

    private static Order order(OrderStatus status, OrderItem... items) {
        double total = Arrays.stream(items).mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        return new Order("o", USER, USER, List.of(items), Math.round(total * 100) / 100.0, status,
                PaymentMethod.PAY_ON_DELIVERY, "12 Main Street", now, now, 0L);
    }

    private void givenBuyerOrders(Order... orders) {
        when(orderRepository.findByBuyerIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(orders));
    }

    private void givenSellerOrders(Order... orders) {
        when(orderRepository.findBySellerIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(orders));
    }

    @Test
    void clientStats_areEmpty_whenThereAreNoOrders() {
        givenBuyerOrders();

        ClientStatsDTO stats = statsService.clientStats(USER);

        assertThat(stats.getTotalSpent()).isZero();
        assertThat(stats.getOrderCount()).isZero();
        assertThat(stats.getMostBought()).isEmpty();
        assertThat(stats.getBestProducts()).isEmpty();
    }

    @Test
    void clientStats_addUpTheMoneySpentAndTheOrders() {
        givenBuyerOrders(
                order(OrderStatus.DELIVERED, item("p1", "Book", 12.5, 2)),
                order(OrderStatus.PENDING, item("p2", "Pen", 2.0, 5), item("p1", "Book", 12.5, 1)));

        ClientStatsDTO stats = statsService.clientStats(USER);

        assertThat(stats.getOrderCount()).isEqualTo(2);
        assertThat(stats.getTotalSpent()).isEqualTo(47.5);
    }

    @Test
    void clientStats_leaveOutCancelledOrdersEverywhere() {
        givenBuyerOrders(
                order(OrderStatus.DELIVERED, item("p1", "Book", 10.0, 1)),
                order(OrderStatus.CANCELLED, item("p1", "Book", 10.0, 50), item("p9", "Yacht", 999.0, 1)));

        ClientStatsDTO stats = statsService.clientStats(USER);

        assertThat(stats.getOrderCount()).isEqualTo(1);
        assertThat(stats.getTotalSpent()).isEqualTo(10.0);
        assertThat(stats.getMostBought()).extracting(ProductStatDTO::getProductId).containsExactly("p1");
        assertThat(stats.getMostBought().get(0).getQuantity()).isEqualTo(1);
        assertThat(stats.getBestProducts()).extracting(ProductStatDTO::getProductId).containsExactly("p1");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "SHIPPED", "DELIVERED"})
    void clientStats_countEveryOrderThatIsNotCancelled(OrderStatus status) {
        givenBuyerOrders(order(status, item("p1", "Book", 10.0, 3)));

        ClientStatsDTO stats = statsService.clientStats(USER);

        assertThat(stats.getOrderCount()).isEqualTo(1);
        assertThat(stats.getTotalSpent()).isEqualTo(30.0);
    }

    @Test
    void clientStats_addUpTheSameProductAcrossOrders() {
        givenBuyerOrders(
                order(OrderStatus.DELIVERED, item("p1", "Book", 12.5, 2)),
                order(OrderStatus.SHIPPED, item("p1", "Book", 15.0, 3)));

        ProductStatDTO book = statsService.clientStats(USER).getMostBought().get(0);

        assertThat(book.getQuantity()).isEqualTo(5);
        assertThat(book.getAmount()).isEqualTo(70.0);
    }

    @Test
    void clientStats_rankMostBoughtByQuantity_andBestProductsByMoney() {
        givenBuyerOrders(order(OrderStatus.DELIVERED,
                item("cheap", "Pen", 1.0, 10), item("dear", "Watch", 50.0, 1), item("mid", "Book", 8.0, 4)));

        ClientStatsDTO stats = statsService.clientStats(USER);

        assertThat(stats.getMostBought()).extracting(ProductStatDTO::getProductId).containsExactly("cheap", "mid", "dear");
        assertThat(stats.getBestProducts()).extracting(ProductStatDTO::getProductId).containsExactly("dear", "mid", "cheap");
    }

    @Test
    void clientStats_breakQuantityTiesWithTheMoney_thenTheName() {
        givenBuyerOrders(order(OrderStatus.DELIVERED,
                item("b", "Bravo", 5.0, 2), item("a", "Alpha", 5.0, 2), item("c", "Charlie", 9.0, 2)));

        List<ProductStatDTO> mostBought = statsService.clientStats(USER).getMostBought();

        assertThat(mostBought).extracting(ProductStatDTO::getName).containsExactly("Charlie", "Alpha", "Bravo");
    }

    @Test
    void clientStats_breakMoneyTiesWithTheQuantity_thenTheName() {
        givenBuyerOrders(order(OrderStatus.DELIVERED,
                item("a", "Alpha", 10.0, 1), item("b", "Bravo", 5.0, 2), item("c", "Charlie", 2.0, 5)));

        List<ProductStatDTO> best = statsService.clientStats(USER).getBestProducts();

        assertThat(best).extracting(ProductStatDTO::getName).containsExactly("Charlie", "Bravo", "Alpha");
    }

    @Test
    void clientStats_showOnlyTheTopFive() {
        OrderItem[] items = IntStream.rangeClosed(1, 7)
                .mapToObj(n -> item("p" + n, "Product " + n, n, n))
                .toArray(OrderItem[]::new);
        givenBuyerOrders(order(OrderStatus.DELIVERED, items));

        ClientStatsDTO stats = statsService.clientStats(USER);

        assertThat(stats.getMostBought()).extracting(ProductStatDTO::getProductId)
                .containsExactly("p7", "p6", "p5", "p4", "p3");
        assertThat(stats.getBestProducts()).hasSize(5);
    }

    @Test
    void clientStats_showTheNameFromTheMostRecentOrder() {
        givenBuyerOrders(
                order(OrderStatus.DELIVERED, item("p1", "Book, second edition", 12.0, 1)),
                order(OrderStatus.DELIVERED, item("p1", "Book", 10.0, 1)));

        assertThat(statsService.clientStats(USER).getMostBought().get(0).getName()).isEqualTo("Book, second edition");
    }

    @Test
    void clientStats_getRidOfFloatingPointNoise() {
        givenBuyerOrders(
                order(OrderStatus.DELIVERED, item("p1", "Pen", 0.1, 3)),
                order(OrderStatus.DELIVERED, item("p1", "Pen", 0.2, 1)));

        ClientStatsDTO stats = statsService.clientStats(USER);

        assertThat(stats.getTotalSpent()).isEqualTo(0.5);
        assertThat(stats.getMostBought().get(0).getAmount()).isEqualTo(0.5);
    }

    @Test
    void sellerStats_areEmpty_whenNothingWasOrdered() {
        givenSellerOrders();

        SellerStatsDTO stats = statsService.sellerStats(USER);

        assertThat(stats.getTotalEarned()).isZero();
        assertThat(stats.getOrderCount()).isZero();
        assertThat(stats.getBestSelling()).isEmpty();
    }

    @Test
    void sellerStats_addUpWhatWasEarned_andRankTheBestSellers() {
        givenSellerOrders(
                order(OrderStatus.DELIVERED, item("p1", "Book", 12.5, 2), item("p2", "Pen", 1.0, 10)),
                order(OrderStatus.PENDING, item("p2", "Pen", 1.0, 5)),
                order(OrderStatus.SHIPPED, item("p3", "Mug", 8.0, 1)));

        SellerStatsDTO stats = statsService.sellerStats(USER);

        assertThat(stats.getOrderCount()).isEqualTo(3);
        assertThat(stats.getTotalEarned()).isEqualTo(48.0);
        assertThat(stats.getBestSelling()).extracting(ProductStatDTO::getProductId).containsExactly("p2", "p1", "p3");
        assertThat(stats.getBestSelling().get(0).getQuantity()).isEqualTo(15);
        assertThat(stats.getBestSelling().get(0).getAmount()).isEqualTo(15.0);
    }

    @Test
    void sellerStats_leaveOutCancelledOrders() {
        givenSellerOrders(
                order(OrderStatus.DELIVERED, item("p1", "Book", 10.0, 1)),
                order(OrderStatus.CANCELLED, item("p1", "Book", 10.0, 100)));

        SellerStatsDTO stats = statsService.sellerStats(USER);

        assertThat(stats.getOrderCount()).isEqualTo(1);
        assertThat(stats.getTotalEarned()).isEqualTo(10.0);
        assertThat(stats.getBestSelling().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    void sellerStats_showOnlyTheTopFive() {
        OrderItem[] items = IntStream.rangeClosed(1, 6)
                .mapToObj(n -> item("p" + n, "Product " + n, 1.0, n))
                .toArray(OrderItem[]::new);
        givenSellerOrders(order(OrderStatus.DELIVERED, items));

        assertThat(statsService.sellerStats(USER).getBestSelling()).hasSize(5);
    }
}
