package platform.eshop.plaza.orderservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import platform.eshop.plaza.orderservice.dto.OrderResponseDTO;
import platform.eshop.plaza.orderservice.entity.Order;
import platform.eshop.plaza.orderservice.entity.OrderItem;
import platform.eshop.plaza.orderservice.enums.OrderStatus;
import platform.eshop.plaza.orderservice.enums.PaymentMethod;
import platform.eshop.plaza.orderservice.exception.NotOrderParticipantException;
import platform.eshop.plaza.orderservice.exception.OrderNotFoundException;
import platform.eshop.plaza.orderservice.repository.OrderRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceListingTest {

    private static final String BUYER = "client-1";
    private static final String SELLER = "seller-1";

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private static Order order(String id, OrderStatus status, String address, String... productNames) {
        List<OrderItem> items = Arrays.stream(productNames)
                .map(name -> new OrderItem("p-" + name, name, 10.0, 1))
                .toList();
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        return new Order(id, BUYER, SELLER, items, 10.0 * items.size(), status,
                PaymentMethod.PAY_ON_DELIVERY, address, now, now, 0L);
    }

    private void givenBuyerOrders(Order... orders) {
        when(orderRepository.findByBuyerIdOrderByCreatedAtDesc(BUYER)).thenReturn(List.of(orders));
    }

    @Test
    void listForBuyer_returnsTheOrdersInTheOrderTheRepositoryGaveThem() {
        givenBuyerOrders(
                order("o3", OrderStatus.PENDING, "Athens", "Book"),
                order("o2", OrderStatus.SHIPPED, "Athens", "Pen"),
                order("o1", OrderStatus.DELIVERED, "Athens", "Mug"));

        List<OrderResponseDTO> orders = orderService.listForBuyer(BUYER, null, null);

        assertThat(orders).extracting("id").containsExactly("o3", "o2", "o1");
    }

    @Test
    void listForBuyer_isEmpty_whenThereAreNoOrders() {
        givenBuyerOrders();

        assertThat(orderService.listForBuyer(BUYER, null, null)).isEmpty();
    }

    @Test
    void listForBuyer_filtersByStatus() {
        givenBuyerOrders(
                order("o3", OrderStatus.PENDING, "Athens", "Book"),
                order("o2", OrderStatus.CANCELLED, "Athens", "Pen"),
                order("o1", OrderStatus.CANCELLED, "Athens", "Mug"));

        List<OrderResponseDTO> orders = orderService.listForBuyer(BUYER, null, OrderStatus.CANCELLED);

        assertThat(orders).extracting("id").containsExactly("o2", "o1");
    }

    @Test
    void listForBuyer_searchesProductNames_ignoringCase() {
        givenBuyerOrders(
                order("o2", OrderStatus.PENDING, "Athens", "Blue Mug", "Pen"),
                order("o1", OrderStatus.PENDING, "Athens", "Book"));

        List<OrderResponseDTO> orders = orderService.listForBuyer(BUYER, "  MUG ", null);

        assertThat(orders).extracting("id").containsExactly("o2");
    }

    @Test
    void listForBuyer_searchesTheOrderId() {
        givenBuyerOrders(
                order("6ab00c11", OrderStatus.PENDING, "Athens", "Book"),
                order("6ab00d22", OrderStatus.PENDING, "Athens", "Book"));

        List<OrderResponseDTO> orders = orderService.listForBuyer(BUYER, "00D2", null);

        assertThat(orders).extracting("id").containsExactly("6ab00d22");
    }

    @Test
    void listForBuyer_searchesTheDeliveryAddress() {
        givenBuyerOrders(
                order("o2", OrderStatus.PENDING, "12 Main Street, Athens", "Book"),
                order("o1", OrderStatus.PENDING, "4 Harbour Road, Patras", "Book"));

        List<OrderResponseDTO> orders = orderService.listForBuyer(BUYER, "patras", null);

        assertThat(orders).extracting("id").containsExactly("o1");
    }

    @Test
    void listForBuyer_combinesTheSearchWithTheStatus() {
        givenBuyerOrders(
                order("o3", OrderStatus.PENDING, "Athens", "Book"),
                order("o2", OrderStatus.DELIVERED, "Athens", "Book"),
                order("o1", OrderStatus.DELIVERED, "Athens", "Pen"));

        List<OrderResponseDTO> orders = orderService.listForBuyer(BUYER, "book", OrderStatus.DELIVERED);

        assertThat(orders).extracting("id").containsExactly("o2");
    }

    @Test
    void listForBuyer_ignoresABlankSearch() {
        givenBuyerOrders(order("o1", OrderStatus.PENDING, "Athens", "Book"));

        assertThat(orderService.listForBuyer(BUYER, "   ", null)).hasSize(1);
    }

    @Test
    void listForBuyer_findsNothing_whenNothingMatches() {
        givenBuyerOrders(order("o1", OrderStatus.PENDING, "Athens", "Book"));

        assertThat(orderService.listForBuyer(BUYER, "unicorn", null)).isEmpty();
    }

    @Test
    void listForBuyer_copesWithOrdersWithoutAnAddress() {
        givenBuyerOrders(order("o1", OrderStatus.PENDING, null, "Book"));

        assertThat(orderService.listForBuyer(BUYER, "athens", null)).isEmpty();
        assertThat(orderService.listForBuyer(BUYER, "book", null)).hasSize(1);
    }

    @Test
    void listForSeller_readsTheOrdersReceivedBySeller_withTheSameFilters() {
        when(orderRepository.findBySellerIdOrderByCreatedAtDesc(SELLER)).thenReturn(List.of(
                order("o2", OrderStatus.PENDING, "Athens", "Book"),
                order("o1", OrderStatus.SHIPPED, "Athens", "Book")));

        assertThat(orderService.listForSeller(SELLER, null, null)).extracting("id").containsExactly("o2", "o1");
        assertThat(orderService.listForSeller(SELLER, "book", OrderStatus.SHIPPED)).extracting("id").containsExactly("o1");
    }

    @Test
    void getOrder_isVisibleToTheBuyer() {
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order("o1", OrderStatus.PENDING, "Athens", "Book")));

        OrderResponseDTO order = orderService.getOrder("o1", BUYER);

        assertThat(order.getId()).isEqualTo("o1");
        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    void getOrder_isVisibleToTheSeller() {
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order("o1", OrderStatus.PENDING, "Athens", "Book")));

        assertThat(orderService.getOrder("o1", SELLER).getId()).isEqualTo("o1");
    }

    @Test
    void getOrder_isHiddenFromEveryoneElse() {
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order("o1", OrderStatus.PENDING, "Athens", "Book")));

        assertThatThrownBy(() -> orderService.getOrder("o1", "stranger"))
                .isInstanceOf(NotOrderParticipantException.class)
                .hasMessage("You can only see orders that you placed or received");
    }

    @Test
    void getOrder_reportsAMissingOrder() {
        when(orderRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("nope", BUYER))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order with id: nope not found");
    }
}
