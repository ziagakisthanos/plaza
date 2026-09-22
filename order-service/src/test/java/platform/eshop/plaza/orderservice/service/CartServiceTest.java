package platform.eshop.plaza.orderservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import platform.eshop.plaza.orderservice.client.ProductClient;
import platform.eshop.plaza.orderservice.dto.CartResponseDTO;
import platform.eshop.plaza.orderservice.dto.ProductDTO;
import platform.eshop.plaza.orderservice.entity.Cart;
import platform.eshop.plaza.orderservice.entity.CartItem;
import platform.eshop.plaza.orderservice.exception.CartConflictException;
import platform.eshop.plaza.orderservice.exception.CartItemNotFoundException;
import platform.eshop.plaza.orderservice.exception.InsufficientStockException;
import platform.eshop.plaza.orderservice.exception.ProductServiceUnavailableException;
import platform.eshop.plaza.orderservice.exception.ProductUnavailableException;
import platform.eshop.plaza.orderservice.repository.CartRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final String USER = "client-1";

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private CartService cartService;

    private static ProductDTO product(String id, String name, double price, int stock) {
        return new ProductDTO(id, name, price, stock, "seller-1");
    }

    private static Cart cartWith(CartItem... items) {
        return new Cart("cart-1", USER, new ArrayList<>(List.of(items)), null, 1L);
    }

    private Cart savedCart() {
        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void getCart_isEmpty_whenTheClientHasNoCart_andDoesNotCallTheProductService() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.empty());

        CartResponseDTO cart = cartService.getCart(USER);

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getItemCount()).isZero();
        assertThat(cart.getTotal()).isZero();
        verifyNoInteractions(productClient);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void getCart_showsCurrentNamesPricesStockAndTotals() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.of(cartWith(new CartItem("p1", 2), new CartItem("p2", 3))));
        when(productClient.lookup(List.of("p1", "p2"))).thenReturn(List.of(
                product("p1", "Book", 12.5, 10),
                product("p2", "Pen", 0.1, 2)));

        CartResponseDTO cart = cartService.getCart(USER);

        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.getItems().get(0).getName()).isEqualTo("Book");
        assertThat(cart.getItems().get(0).getPrice()).isEqualTo(12.5);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(cart.getItems().get(0).getAvailableStock()).isEqualTo(10);
        assertThat(cart.getItems().get(0).getLineTotal()).isEqualTo(25.0);
        assertThat(cart.getItems().get(1).getLineTotal()).isEqualTo(0.3);
        assertThat(cart.getItems().get(1).getAvailableStock()).isEqualTo(2);
        assertThat(cart.getItemCount()).isEqualTo(5);
        assertThat(cart.getTotal()).isEqualTo(25.3);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void getCart_dropsProductsThatNoLongerExist_andSavesTheCleanCart() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.of(cartWith(new CartItem("p1", 1), new CartItem("gone", 4))));
        when(productClient.lookup(List.of("p1", "gone"))).thenReturn(List.of(product("p1", "Book", 10.0, 5)));

        CartResponseDTO cart = cartService.getCart(USER);

        assertThat(cart.getItems()).extracting("productId").containsExactly("p1");
        assertThat(cart.getTotal()).isEqualTo(10.0);
        assertThat(savedCart().getItems()).extracting("productId").containsExactly("p1");
    }

    @Test
    void getCart_letsAProductServiceOutageThrough() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));
        when(productClient.lookup(List.of("p1"))).thenThrow(new ProductServiceUnavailableException("down"));

        assertThatThrownBy(() -> cartService.getCart(USER)).isInstanceOf(ProductServiceUnavailableException.class);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_createsTheCartForANewClient() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.empty());
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 12.5, 10)));

        CartResponseDTO cart = cartService.addItem(USER, "p1", 2);

        Cart saved = savedCart();
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getProductId()).isEqualTo("p1");
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(cart.getTotal()).isEqualTo(25.0);
    }

    @Test
    void addItem_addsToTheExistingLine_insteadOfCreatingASecondOne() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 2))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 10)));

        CartResponseDTO cart = cartService.addItem(USER, "p1", 3);

        assertThat(savedCart().getItems()).hasSize(1);
        assertThat(savedCart().getItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(cart.getItemCount()).isEqualTo(5);
    }

    @Test
    void addItem_looksUpTheNewProductTogetherWithTheOnesAlreadyInTheCart() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));
        when(productClient.lookup(List.of("p1", "p2"))).thenReturn(List.of(
                product("p1", "Book", 10.0, 5), product("p2", "Pen", 2.0, 5)));

        CartResponseDTO cart = cartService.addItem(USER, "p2", 1);

        assertThat(cart.getItems()).extracting("productId").containsExactly("p1", "p2");
        assertThat(cart.getTotal()).isEqualTo(12.0);
    }

    @Test
    void addItem_allowsExactlyTheAvailableStock() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 3)));

        cartService.addItem(USER, "p1", 2);

        assertThat(savedCart().getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    void addItem_refusesMoreThanTheStock_countingWhatIsAlreadyInTheCart() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 2))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 3)));

        assertThatThrownBy(() -> cartService.addItem(USER, "p1", 2))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Book': only 3 left");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_refusesAProductThatIsSoldOut() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.empty());
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 0)));

        assertThatThrownBy(() -> cartService.addItem(USER, "p1", 1))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Book': only 0 left");
    }

    @Test
    void addItem_doesNotOverflowOnHugeQuantities() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 5))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 100)));

        assertThatThrownBy(() -> cartService.addItem(USER, "p1", Integer.MAX_VALUE))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void addItem_refusesAnUnknownProduct() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.empty());
        when(productClient.lookup(List.of("nope"))).thenReturn(List.of());

        assertThatThrownBy(() -> cartService.addItem(USER, "nope", 1))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessage("Product with id: nope not found");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_dropsOtherProductsThatWereDeleted() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.of(cartWith(new CartItem("gone", 1))));
        when(productClient.lookup(List.of("gone", "p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 5)));

        CartResponseDTO cart = cartService.addItem(USER, "p1", 1);

        assertThat(savedCart().getItems()).extracting("productId").containsExactly("p1");
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void updateItem_setsTheQuantity() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 5))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 10)));

        CartResponseDTO cart = cartService.updateItem(USER, "p1", 2);

        assertThat(savedCart().getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(cart.getTotal()).isEqualTo(20.0);
    }

    @Test
    void updateItem_refusesMoreThanTheStock() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 4)));

        assertThatThrownBy(() -> cartService.updateItem(USER, "p1", 5))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Book': only 4 left");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateItem_refusesAProductThatIsNotInTheCart() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));

        assertThatThrownBy(() -> cartService.updateItem(USER, "p2", 1))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessage("Product with id: p2 is not in your cart");
        verifyNoInteractions(productClient);
    }

    @Test
    void updateItem_refusesAProductThatWasDeleted() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of());

        assertThatThrownBy(() -> cartService.updateItem(USER, "p1", 2))
                .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    void removeItem_removesTheLine_andReturnsTheRemainingCart() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.of(cartWith(new CartItem("p1", 1), new CartItem("p2", 2))));
        when(productClient.lookup(List.of("p2"))).thenReturn(List.of(product("p2", "Pen", 2.0, 9)));

        CartResponseDTO cart = cartService.removeItem(USER, "p1");

        assertThat(savedCart().getItems()).extracting("productId").containsExactly("p2");
        assertThat(cart.getItems()).extracting("productId").containsExactly("p2");
        assertThat(cart.getTotal()).isEqualTo(4.0);
    }

    @Test
    void removeItem_lastLine_leavesAnEmptyCart_withoutCallingTheProductService() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));

        CartResponseDTO cart = cartService.removeItem(USER, "p1");

        assertThat(cart.getItems()).isEmpty();
        assertThat(savedCart().getItems()).isEmpty();
        verifyNoInteractions(productClient);
    }

    @Test
    void removeItem_refusesAProductThatIsNotInTheCart() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));

        assertThatThrownBy(() -> cartService.removeItem(USER, "p2"))
                .isInstanceOf(CartItemNotFoundException.class);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_tryingAgainAfterALostRaceToCreateTheFirstCart_addsToTheCartThatWonTheRace() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.empty(), Optional.of(cartWith(new CartItem("p1", 1))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 10)));
        when(cartRepository.save(any(Cart.class)))
                .thenThrow(new DuplicateKeyException("userId already exists"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDTO cart = cartService.addItem(USER, "p1", 1);

        assertThat(cart.getItemCount()).isEqualTo(2);
        verify(cartRepository, times(2)).save(any(Cart.class));
    }

    @Test
    void addItem_readsTheCartAgainWhenSomeoneElseChangedItInTheMeantime() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.of(cartWith(new CartItem("p1", 1))),
                        Optional.of(cartWith(new CartItem("p1", 4))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 50)));
        when(cartRepository.save(any(Cart.class)))
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDTO cart = cartService.addItem(USER, "p1", 1);

        assertThat(cart.getItemCount()).isEqualTo(5);
    }

    @Test
    void getCart_retriesWhenCleaningUpDeletedProductsConflicts() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.of(cartWith(new CartItem("gone", 1))),
                        Optional.of(cartWith(new CartItem("gone", 1))));
        when(productClient.lookup(List.of("gone"))).thenReturn(List.of());
        when(cartRepository.save(any(Cart.class)))
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDTO cart = cartService.getCart(USER);

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository, times(2)).save(any(Cart.class));
    }

    @Test
    void removeItem_retriesOnConflict() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.of(cartWith(new CartItem("p1", 1))),
                        Optional.of(cartWith(new CartItem("p1", 1))));
        when(cartRepository.save(any(Cart.class)))
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDTO cart = cartService.removeItem(USER, "p1");

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void updateItem_retriesOnConflict() {
        when(cartRepository.findByUserId(USER))
                .thenReturn(Optional.of(cartWith(new CartItem("p1", 1))),
                        Optional.of(cartWith(new CartItem("p1", 1))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 9)));
        when(cartRepository.save(any(Cart.class)))
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDTO cart = cartService.updateItem(USER, "p1", 3);

        assertThat(cart.getItemCount()).isEqualTo(3);
    }

    @Test
    void addItem_givesUpWithAConflictError_afterRepeatedCollisions() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cartWith(new CartItem("p1", 1))));
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 50)));
        when(cartRepository.save(any(Cart.class))).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> cartService.addItem(USER, "p1", 1))
                .isInstanceOf(CartConflictException.class)
                .hasMessage("Your cart is being changed elsewhere, please try again");
        verify(cartRepository, times(5)).save(any(Cart.class));
    }

    @Test
    void addItem_doesNotRetryBusinessErrors() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.empty());
        when(productClient.lookup(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 10.0, 0)));

        assertThatThrownBy(() -> cartService.addItem(USER, "p1", 1))
                .isInstanceOf(InsufficientStockException.class);
        verify(productClient, times(1)).lookup(List.of("p1"));
    }

    @Test
    void clear_deletesTheCartOfThatClientOnly() {
        cartService.clear(USER);

        verify(cartRepository).deleteByUserId(USER);
    }
}
