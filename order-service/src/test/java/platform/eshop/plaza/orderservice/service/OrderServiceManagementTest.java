package platform.eshop.plaza.orderservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.mongodb.core.MongoTemplate;
import platform.eshop.plaza.orderservice.client.ProductClient;
import platform.eshop.plaza.orderservice.dto.OrderResponseDTO;
import platform.eshop.plaza.orderservice.dto.ProductDTO;
import platform.eshop.plaza.orderservice.dto.StockItemDTO;
import platform.eshop.plaza.orderservice.entity.Order;
import platform.eshop.plaza.orderservice.entity.OrderItem;
import platform.eshop.plaza.orderservice.enums.OrderStatus;
import platform.eshop.plaza.orderservice.enums.PaymentMethod;
import platform.eshop.plaza.orderservice.exception.InsufficientStockException;
import platform.eshop.plaza.orderservice.exception.InvalidOrderStateException;
import platform.eshop.plaza.orderservice.exception.NotOrderParticipantException;
import platform.eshop.plaza.orderservice.exception.OrderNotFoundException;
import platform.eshop.plaza.orderservice.exception.ProductServiceUnavailableException;
import platform.eshop.plaza.orderservice.repository.OrderRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceManagementTest {

    private static final String BUYER = "client-1";
    private static final String SELLER = "seller-1";
    private static final Instant CREATED = Instant.parse("2026-03-01T10:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private OrderService orderService;

    private static Order order(OrderStatus status) {
        List<OrderItem> items = List.of(new OrderItem("p1", "Book", 12.5, 2), new OrderItem("p2", "Pen", 1.0, 3));
        return new Order("o1", BUYER, SELLER, items, 28.0, status,
                PaymentMethod.PAY_ON_DELIVERY, "12 Main Street, Athens", CREATED, CREATED, 4L);
    }

    private void givenOrder(OrderStatus status) {
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order(status)));
    }

    private void savingReturnsTheOrder() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Order savedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    static Stream<Arguments> forbiddenTransitions() {
        List<Arguments> pairs = new ArrayList<>();
        for (OrderStatus current : OrderStatus.values()) {
            for (OrderStatus target : OrderStatus.values()) {
                if (current.next().filter(next -> next == target).isEmpty()) {
                    pairs.add(Arguments.of(current, target));
                }
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest
    @CsvSource({"PENDING,CONFIRMED", "CONFIRMED,SHIPPED", "SHIPPED,DELIVERED"})
    void updateStatus_movesTheOrderOneStepForward(OrderStatus current, OrderStatus target) {
        givenOrder(current);
        savingReturnsTheOrder();

        OrderResponseDTO result = orderService.updateStatus("o1", SELLER, target);

        assertThat(result.getStatus()).isEqualTo(target);
        assertThat(savedOrder().getStatus()).isEqualTo(target);
        assertThat(result.getUpdatedAt()).isAfter(CREATED);
        assertThat(result.getCreatedAt()).isEqualTo(CREATED);
    }

    @ParameterizedTest
    @MethodSource("forbiddenTransitions")
    void updateStatus_refusesEveryOtherChange(OrderStatus current, OrderStatus target) {
        givenOrder(current);

        assertThatThrownBy(() -> orderService.updateStatus("o1", SELLER, target))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("An order that is " + current + " cannot be changed to " + target);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void forbiddenTransitions_coverEverythingExceptTheThreeForwardSteps() {
        assertThat(forbiddenTransitions().count()).isEqualTo(22);
    }

    @ParameterizedTest
    @CsvSource({"client-1", "stranger", "seller-2"})
    void updateStatus_isOnlyForTheSellerOfTheOrder(String userId) {
        givenOrder(OrderStatus.PENDING);

        assertThatThrownBy(() -> orderService.updateStatus("o1", userId, OrderStatus.CONFIRMED))
                .isInstanceOf(NotOrderParticipantException.class)
                .hasMessage("Only the seller of an order can change its status");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateStatus_reportsAMissingOrder() {
        when(orderRepository.findById("o1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus("o1", SELLER, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void updateStatus_letsAConcurrentChangeSurface() {
        givenOrder(OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> orderService.updateStatus("o1", SELLER, OrderStatus.CONFIRMED))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @ParameterizedTest
    @CsvSource({"PENDING,client-1", "PENDING,seller-1", "CONFIRMED,client-1", "CONFIRMED,seller-1"})
    void cancel_cancelsTheOrderForItsBuyerOrSeller_andGivesTheStockBack(OrderStatus current, String userId) {
        givenOrder(current);
        savingReturnsTheOrder();

        OrderResponseDTO result = orderService.cancel("o1", userId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(savedOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getUpdatedAt()).isAfter(CREATED);
        ArgumentCaptor<List<StockItemDTO>> released = ArgumentCaptor.forClass(List.class);
        verify(productClient).release(released.capture());
        assertThat(released.getValue()).extracting("productId").containsExactly("p1", "p2");
        assertThat(released.getValue()).extracting("quantity").containsExactly(2, 3);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED", "CANCELLED"})
    void cancel_refusesOrdersThatAreTooFarAlongOrAlreadyCancelled(OrderStatus current) {
        givenOrder(current);

        assertThatThrownBy(() -> orderService.cancel("o1", BUYER))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Only pending or confirmed orders can be cancelled");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(productClient);
    }

    @Test
    void cancel_isRefusedForSomeoneWhoIsNotInTheOrder() {
        givenOrder(OrderStatus.PENDING);

        assertThatThrownBy(() -> orderService.cancel("o1", "stranger"))
                .isInstanceOf(NotOrderParticipantException.class)
                .hasMessage("You can only cancel orders that you placed or received");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(productClient);
    }

    @Test
    void cancel_doesNotReleaseStock_whenTheOrderWasChangedMeanwhile() {
        givenOrder(OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> orderService.cancel("o1", BUYER))
                .isInstanceOf(OptimisticLockingFailureException.class);
        verifyNoInteractions(productClient);
    }

    @Test
    void cancel_stillCancelsTheOrder_whenTheProductServiceCannotTakeTheStockBackRightNow() {
        givenOrder(OrderStatus.PENDING);
        savingReturnsTheOrder();
        doThrow(new ProductServiceUnavailableException("down")).when(productClient).release(anyList());

        OrderResponseDTO result = orderService.cancel("o1", BUYER);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_reportsAMissingOrder() {
        when(orderRepository.findById("o1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancel("o1", BUYER)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void remove_deletesACancelledOrderOfTheBuyer() {
        givenOrder(OrderStatus.CANCELLED);

        orderService.remove("o1", BUYER);

        verify(orderRepository).delete(any(Order.class));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "SHIPPED", "DELIVERED"})
    void remove_refusesOrdersThatAreNotCancelled(OrderStatus current) {
        givenOrder(current);

        assertThatThrownBy(() -> orderService.remove("o1", BUYER))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Only cancelled orders can be removed");
        verify(orderRepository, never()).delete(any(Order.class));
    }

    @ParameterizedTest
    @CsvSource({"seller-1", "stranger"})
    void remove_isOnlyForTheBuyer(String userId) {
        givenOrder(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderService.remove("o1", userId))
                .isInstanceOf(NotOrderParticipantException.class)
                .hasMessage("Only the buyer can remove an order");
        verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    void remove_reportsAMissingOrder() {
        when(orderRepository.findById("o1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.remove("o1", BUYER)).isInstanceOf(OrderNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED"})
    void redo_ordersTheSameItemsAgain_atTodaysPrices(OrderStatus current) {
        givenOrder(current);
        when(productClient.reserve(anyList())).thenReturn(List.of(
                new ProductDTO("p1", "Book", 14.0, 8, SELLER), new ProductDTO("p2", "Pen", 1.5, 8, SELLER)));
        when(orderRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseDTO result = orderService.redo("o1", BUYER);

        assertThat(result.getId()).isNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getBuyerId()).isEqualTo(BUYER);
        assertThat(result.getSellerId()).isEqualTo(SELLER);
        assertThat(result.getDeliveryAddress()).isEqualTo("12 Main Street, Athens");
        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.PAY_ON_DELIVERY);
        assertThat(result.getItems()).extracting("price").containsExactly(14.0, 1.5);
        assertThat(result.getItems()).extracting("quantity").containsExactly(2, 3);
        assertThat(result.getTotal()).isEqualTo(32.5);
        assertThat(result.getCreatedAt()).isAfter(CREATED);
        verify(orderRepository, never()).save(any());
        ArgumentCaptor<List<StockItemDTO>> reserved = ArgumentCaptor.forClass(List.class);
        verify(productClient).reserve(reserved.capture());
        assertThat(reserved.getValue()).extracting("productId").containsExactly("p1", "p2");
        assertThat(reserved.getValue()).extracting("quantity").containsExactly(2, 3);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "SHIPPED"})
    void redo_refusesOrdersThatAreStillActive(OrderStatus current) {
        givenOrder(current);

        assertThatThrownBy(() -> orderService.redo("o1", BUYER))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Only delivered or cancelled orders can be ordered again");
        verifyNoInteractions(productClient);
    }

    @ParameterizedTest
    @CsvSource({"seller-1", "stranger"})
    void redo_isOnlyForTheBuyer(String userId) {
        givenOrder(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> orderService.redo("o1", userId))
                .isInstanceOf(NotOrderParticipantException.class)
                .hasMessage("Only the buyer can order the same items again");
        verifyNoInteractions(productClient);
    }

    @Test
    void redo_fails_whenTheStockIsNotThereAnyMore_andCreatesNothing() {
        givenOrder(OrderStatus.DELIVERED);
        when(productClient.reserve(anyList()))
                .thenThrow(new InsufficientStockException("Not enough stock for 'Book': only 1 left"));

        assertThatThrownBy(() -> orderService.redo("o1", BUYER))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Book': only 1 left");
        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void redo_givesTheStockBack_whenTheNewOrderCannotBeSaved() {
        givenOrder(OrderStatus.DELIVERED);
        when(productClient.reserve(anyList())).thenReturn(List.of(
                new ProductDTO("p1", "Book", 14.0, 8, SELLER), new ProductDTO("p2", "Pen", 1.5, 8, SELLER)));
        when(orderRepository.saveAll(anyList())).thenThrow(new QueryTimeoutException("slow"));

        assertThatThrownBy(() -> orderService.redo("o1", BUYER)).isInstanceOf(QueryTimeoutException.class);
        verify(productClient).release(anyList());
    }

    @Test
    void redo_reportsAMissingOrder() {
        when(orderRepository.findById("o1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.redo("o1", BUYER)).isInstanceOf(OrderNotFoundException.class);
    }
}
