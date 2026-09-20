package platform.zone01.orderservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import platform.zone01.orderservice.dto.AddCartItemRequestDTO;
import platform.zone01.orderservice.dto.CartResponseDTO;
import platform.zone01.orderservice.dto.UpdateCartItemRequestDTO;
import platform.zone01.orderservice.service.CartService;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponseDTO> getCart(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponseDTO> addItem(
            @Valid @RequestBody AddCartItemRequestDTO request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(cartService.addItem(userId, request.getProductId(), request.getQuantity()));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponseDTO> updateItem(
            @PathVariable String productId,
            @Valid @RequestBody UpdateCartItemRequestDTO request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(cartService.updateItem(userId, productId, request.getQuantity()));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponseDTO> removeItem(
            @PathVariable String productId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(cartService.removeItem(userId, productId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal String userId) {
        cartService.clear(userId);
        return ResponseEntity.noContent().build();
    }
}
