package platform.eshop.plaza.orderservice.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import platform.eshop.plaza.orderservice.client.ProductClient;
import platform.eshop.plaza.orderservice.dto.CartItemResponseDTO;
import platform.eshop.plaza.orderservice.dto.CartResponseDTO;
import platform.eshop.plaza.orderservice.dto.ProductDTO;
import platform.eshop.plaza.orderservice.entity.Cart;
import platform.eshop.plaza.orderservice.entity.CartItem;
import platform.eshop.plaza.orderservice.exception.CartConflictException;
import platform.eshop.plaza.orderservice.exception.CartItemNotFoundException;
import platform.eshop.plaza.orderservice.exception.InsufficientStockException;
import platform.eshop.plaza.orderservice.exception.ProductUnavailableException;
import platform.eshop.plaza.orderservice.repository.CartRepository;
import platform.eshop.plaza.orderservice.util.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Keeps each client's cart. The cart stores only product ids and quantities; names, prices and
 * stock are read from the product service every time, so the cart never shows stale prices.
 * Products that no longer exist are dropped from the cart when it is read. Two requests changing
 * the same cart at once are detected with optimistic locking; the loser reads the cart again and
 * repeats its change, so no update is lost.
 */
@Service
public class CartService {

    private static final int MAX_ATTEMPTS = 5;

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    public CartService(CartRepository cartRepository, ProductClient productClient) {
        this.cartRepository = cartRepository;
        this.productClient = productClient;
    }

    public CartResponseDTO getCart(String userId) {
        return retryOnConflict(() -> view(findCart(userId)));
    }

    public CartResponseDTO addItem(String userId, String productId, int quantity) {
        return retryOnConflict(() -> {
            Cart cart = findCart(userId);
            Map<String, ProductDTO> products = lookup(cart, productId);
            ProductDTO product = requireProduct(products, productId);

            long wanted = (long) quantityInCart(cart, productId) + quantity;
            requireStock(product, wanted);

            setQuantity(cart, productId, (int) wanted);
            return saveAndRespond(cart, products);
        });
    }

    public CartResponseDTO updateItem(String userId, String productId, int quantity) {
        return retryOnConflict(() -> {
            Cart cart = findCart(userId);
            requireLine(cart, productId);

            Map<String, ProductDTO> products = lookup(cart);
            ProductDTO product = requireProduct(products, productId);
            requireStock(product, quantity);

            setQuantity(cart, productId, quantity);
            return saveAndRespond(cart, products);
        });
    }

    public CartResponseDTO removeItem(String userId, String productId) {
        return retryOnConflict(() -> {
            Cart cart = findCart(userId);
            requireLine(cart, productId);

            cart.getItems().removeIf(item -> item.getProductId().equals(productId));
            save(cart);
            return view(cart);
        });
    }

    public void clear(String userId) {
        cartRepository.deleteByUserId(userId);
    }

    private CartResponseDTO retryOnConflict(Supplier<CartResponseDTO> action) {
        int attempt = 1;
        while (true) {
            try {
                return action.get();
            } catch (DuplicateKeyException | OptimisticLockingFailureException e) {
                if (attempt++ >= MAX_ATTEMPTS) {
                    throw new CartConflictException("Your cart is being changed elsewhere, please try again");
                }
            }
        }
    }

    private Cart findCart(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> new Cart(null, userId, new ArrayList<>(), null, null));
    }

    private CartResponseDTO view(Cart cart) {
        if (cart.getItems().isEmpty()) {
            return toResponse(cart, Map.of());
        }
        Map<String, ProductDTO> products = lookup(cart);
        if (dropUnavailable(cart, products)) {
            save(cart);
        }
        return toResponse(cart, products);
    }

    private CartResponseDTO saveAndRespond(Cart cart, Map<String, ProductDTO> products) {
        dropUnavailable(cart, products);
        save(cart);
        return toResponse(cart, products);
    }

    private void save(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
    }

    private Map<String, ProductDTO> lookup(Cart cart, String... extraProductIds) {
        Set<String> ids = new LinkedHashSet<>();
        cart.getItems().forEach(item -> ids.add(item.getProductId()));
        ids.addAll(List.of(extraProductIds));
        return productClient.lookup(new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));
    }

    private boolean dropUnavailable(Cart cart, Map<String, ProductDTO> products) {
        return cart.getItems().removeIf(item -> !products.containsKey(item.getProductId()));
    }

    private ProductDTO requireProduct(Map<String, ProductDTO> products, String productId) {
        ProductDTO product = products.get(productId);
        if (product == null) {
            throw new ProductUnavailableException("Product with id: " + productId + " not found");
        }
        return product;
    }

    private void requireStock(ProductDTO product, long wanted) {
        if (wanted > product.getQuantity()) {
            throw new InsufficientStockException(
                    "Not enough stock for '" + product.getName() + "': only " + product.getQuantity() + " left");
        }
    }

    private Optional<CartItem> findLine(Cart cart, String productId) {
        return cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();
    }

    private void requireLine(Cart cart, String productId) {
        if (findLine(cart, productId).isEmpty()) {
            throw new CartItemNotFoundException("Product with id: " + productId + " is not in your cart");
        }
    }

    private int quantityInCart(Cart cart, String productId) {
        return findLine(cart, productId).map(CartItem::getQuantity).orElse(0);
    }

    private void setQuantity(Cart cart, String productId, int quantity) {
        findLine(cart, productId).ifPresentOrElse(
                item -> item.setQuantity(quantity),
                () -> cart.getItems().add(new CartItem(productId, quantity)));
    }

    private CartResponseDTO toResponse(Cart cart, Map<String, ProductDTO> products) {
        List<CartItemResponseDTO> lines = cart.getItems().stream()
                .map(item -> toLine(item, products.get(item.getProductId())))
                .toList();
        int itemCount = lines.stream().mapToInt(CartItemResponseDTO::getQuantity).sum();
        double total = Money.round(lines.stream().mapToDouble(CartItemResponseDTO::getLineTotal).sum());
        return new CartResponseDTO(lines, itemCount, total);
    }

    private CartItemResponseDTO toLine(CartItem item, ProductDTO product) {
        return new CartItemResponseDTO(
                item.getProductId(),
                product.getName(),
                product.getPrice(),
                item.getQuantity(),
                product.getQuantity(),
                Money.round(product.getPrice() * item.getQuantity()));
    }
}
