package platform.eshop.plaza.orderservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import platform.eshop.plaza.orderservice.client.ProductClient;
import platform.eshop.plaza.orderservice.dto.CheckoutRequestDTO;
import platform.eshop.plaza.orderservice.dto.OrderResponseDTO;
import platform.eshop.plaza.orderservice.dto.ProductDTO;
import platform.eshop.plaza.orderservice.dto.StockItemDTO;
import platform.eshop.plaza.orderservice.entity.Cart;
import platform.eshop.plaza.orderservice.entity.CartItem;
import platform.eshop.plaza.orderservice.enums.OrderStatus;
import platform.eshop.plaza.orderservice.enums.PaymentMethod;
import platform.eshop.plaza.orderservice.exception.CartEmptyException;
import platform.eshop.plaza.orderservice.exception.InsufficientStockException;
import platform.eshop.plaza.orderservice.exception.ProductServiceUnavailableException;
import platform.eshop.plaza.orderservice.exception.ProductUnavailableException;
import platform.eshop.plaza.orderservice.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceCheckoutTest {

    private static final String BUYER = "client-1";
    private static final CheckoutRequestDTO REQUEST =
            new CheckoutRequestDTO(PaymentMethod.PAY_ON_DELIVERY, "  12 Main Street, Athens  ");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private OrderService orderService;

    private static ProductDTO product(String id, String name, double price, String seller) {
        return new ProductDTO(id, name, price, 10, seller);
    }

    private static Cart cartWith(CartItem... items) {
        return new Cart("cart-1", BUYER, new ArrayList<>(List.of(items)), null, 3L);
    }

    private void givenCart(Cart cart) {
        when(mongoTemplate.findAndRemove(any(Query.class), eq(Cart.class))).thenReturn(cart);
    }

    private void savedOrdersAreReturnedAsTheyAre() {
        when(orderRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void checkout_takesTheCartOfTheBuyerOutOfTheDatabase() {
        givenCart(cartWith(new CartItem("p1", 1)));
        when(productClient.reserve(anyList())).thenReturn(List.of(product("p1", "Book", 10.0, "seller-1")));
        savedOrdersAreReturnedAsTheyAre();

        orderService.checkout(BUYER, REQUEST);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndRemove(query.capture(), eq(Cart.class));
        assertThat(query.getValue().getQueryObject().toJson()).isEqualTo("{\"userId\": \"client-1\"}");
    }

    @Test
    void checkout_createsOneOrderWithASnapshotOfTheItems() {
        givenCart(cartWith(new CartItem("p1", 2), new CartItem("p2", 3)));
        when(productClient.reserve(anyList())).thenReturn(List.of(
                product("p1", "Book", 12.5, "seller-1"), product("p2", "Pen", 0.1, "seller-1")));
        savedOrdersAreReturnedAsTheyAre();

        List<OrderResponseDTO> orders = orderService.checkout(BUYER, REQUEST);

        assertThat(orders).hasSize(1);
        OrderResponseDTO order = orders.get(0);
        assertThat(order.getBuyerId()).isEqualTo(BUYER);
        assertThat(order.getSellerId()).isEqualTo("seller-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.PAY_ON_DELIVERY);
        assertThat(order.getDeliveryAddress()).isEqualTo("12 Main Street, Athens");
        assertThat(order.getItems()).extracting("name").containsExactly("Book", "Pen");
        assertThat(order.getItems()).extracting("price").containsExactly(12.5, 0.1);
        assertThat(order.getItems()).extracting("quantity").containsExactly(2, 3);
        assertThat(order.getItems()).extracting("lineTotal").containsExactly(25.0, 0.3);
        assertThat(order.getTotal()).isEqualTo(25.3);
        assertThat(order.getCreatedAt()).isNotNull().isEqualTo(order.getUpdatedAt());
    }

    @Test
    void checkout_reservesTheQuantitiesOfTheCart() {
        givenCart(cartWith(new CartItem("p1", 2), new CartItem("p2", 3)));
        when(productClient.reserve(anyList())).thenReturn(List.of(
                product("p1", "Book", 12.5, "seller-1"), product("p2", "Pen", 0.1, "seller-1")));
        savedOrdersAreReturnedAsTheyAre();

        orderService.checkout(BUYER, REQUEST);

        ArgumentCaptor<List<StockItemDTO>> items = ArgumentCaptor.forClass(List.class);
        verify(productClient).reserve(items.capture());
        assertThat(items.getValue()).extracting("productId").containsExactly("p1", "p2");
        assertThat(items.getValue()).extracting("quantity").containsExactly(2, 3);
    }

    @Test
    void checkout_splitsTheCartIntoOneOrderPerSeller() {
        givenCart(cartWith(new CartItem("p1", 1), new CartItem("p2", 2), new CartItem("p3", 1)));
        when(productClient.reserve(anyList())).thenReturn(List.of(
                product("p1", "Book", 10.0, "seller-1"),
                product("p2", "Pen", 2.0, "seller-2"),
                product("p3", "Mug", 5.5, "seller-1")));
        savedOrdersAreReturnedAsTheyAre();

        List<OrderResponseDTO> orders = orderService.checkout(BUYER, REQUEST);

        assertThat(orders).hasSize(2);
        assertThat(orders).extracting("sellerId").containsExactly("seller-1", "seller-2");
        assertThat(orders.get(0).getItems()).extracting("name").containsExactly("Book", "Mug");
        assertThat(orders.get(0).getTotal()).isEqualTo(15.5);
        assertThat(orders.get(1).getItems()).extracting("name").containsExactly("Pen");
        assertThat(orders.get(1).getTotal()).isEqualTo(4.0);
        assertThat(orders).extracting("buyerId").containsOnly(BUYER);
        assertThat(orders).extracting("deliveryAddress").containsOnly("12 Main Street, Athens");
    }

    @Test
    void checkout_doesNotPutTheCartBack_whenItWorked() {
        givenCart(cartWith(new CartItem("p1", 1)));
        when(productClient.reserve(anyList())).thenReturn(List.of(product("p1", "Book", 10.0, "seller-1")));
        savedOrdersAreReturnedAsTheyAre();

        orderService.checkout(BUYER, REQUEST);

        verify(mongoTemplate, never()).insert(any(Cart.class));
        verify(productClient, never()).release(anyList());
    }

    @Test
    void checkout_refusesWhenThereIsNoCart() {
        givenCart(null);

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST))
                .isInstanceOf(CartEmptyException.class)
                .hasMessage("Your cart is empty");
        verifyNoInteractions(productClient, orderRepository);
    }

    @Test
    void checkout_refusesAnEmptyCart() {
        givenCart(cartWith());

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST)).isInstanceOf(CartEmptyException.class);
        verifyNoInteractions(productClient, orderRepository);
    }

    @Test
    void checkout_putsTheCartBack_andCreatesNothing_whenThereIsNotEnoughStock() {
        Cart cart = cartWith(new CartItem("p1", 5));
        givenCart(cart);
        when(productClient.reserve(anyList()))
                .thenThrow(new InsufficientStockException("Not enough stock for 'Book': only 1 left"));

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Book': only 1 left");

        verify(mongoTemplate).insert(cart);
        verifyNoInteractions(orderRepository);
        verify(productClient, never()).release(anyList());
    }

    @Test
    void checkout_putsTheCartBack_whenAProductWasDeleted() {
        Cart cart = cartWith(new CartItem("gone", 1));
        givenCart(cart);
        when(productClient.reserve(anyList())).thenThrow(new ProductUnavailableException("Product with id: gone not found"));

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST)).isInstanceOf(ProductUnavailableException.class);

        verify(mongoTemplate).insert(cart);
    }

    @Test
    void checkout_putsTheCartBack_whenTheProductServiceIsDown() {
        Cart cart = cartWith(new CartItem("p1", 1));
        givenCart(cart);
        when(productClient.reserve(anyList())).thenThrow(new ProductServiceUnavailableException("down"));

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST))
                .isInstanceOf(ProductServiceUnavailableException.class);

        verify(mongoTemplate).insert(cart);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void checkout_releasesTheStockAndPutsTheCartBack_whenTheOrdersCannotBeSaved() {
        Cart cart = cartWith(new CartItem("p1", 2));
        givenCart(cart);
        when(productClient.reserve(anyList())).thenReturn(List.of(product("p1", "Book", 10.0, "seller-1")));
        when(orderRepository.saveAll(anyList())).thenThrow(new QueryTimeoutException("database is slow"));

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST)).isInstanceOf(QueryTimeoutException.class);

        ArgumentCaptor<List<StockItemDTO>> released = ArgumentCaptor.forClass(List.class);
        verify(productClient).release(released.capture());
        assertThat(released.getValue()).extracting("productId").containsExactly("p1");
        assertThat(released.getValue()).extracting("quantity").containsExactly(2);
        verify(mongoTemplate).insert(cart);
    }

    @Test
    void checkout_reportsTheRealProblem_evenWhenReleasingTheStockFailsToo() {
        givenCart(cartWith(new CartItem("p1", 2)));
        when(productClient.reserve(anyList())).thenReturn(List.of(product("p1", "Book", 10.0, "seller-1")));
        when(orderRepository.saveAll(anyList())).thenThrow(new QueryTimeoutException("database is slow"));
        doThrow(new ProductServiceUnavailableException("down")).when(productClient).release(anyList());

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST)).isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void checkout_releasesTheStockAndPutsTheCartBack_whenAReservedProductIsMissingFromTheAnswer() {
        Cart cart = cartWith(new CartItem("p1", 1), new CartItem("p2", 1));
        givenCart(cart);
        when(productClient.reserve(anyList())).thenReturn(List.of(product("p1", "Book", 10.0, "seller-1")));

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessage("Product with id: p2 not found");

        verify(productClient).release(anyList());
        verify(mongoTemplate).insert(cart);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void checkout_keepsTheOriginalError_whenTheCartCannotBePutBackBecauseANewOneExists() {
        Cart cart = cartWith(new CartItem("p1", 1));
        givenCart(cart);
        when(productClient.reserve(anyList())).thenThrow(new InsufficientStockException("no stock"));
        when(mongoTemplate.insert(any(Cart.class))).thenThrow(new DuplicateKeyException("userId exists"));

        assertThatThrownBy(() -> orderService.checkout(BUYER, REQUEST)).isInstanceOf(InsufficientStockException.class);
    }
}
