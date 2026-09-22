package platform.eshop.plaza.orderservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import platform.eshop.plaza.orderservice.dto.CheckoutRequestDTO;
import platform.eshop.plaza.orderservice.dto.OrderResponseDTO;
import platform.eshop.plaza.orderservice.dto.UpdateOrderStatusRequestDTO;
import platform.eshop.plaza.orderservice.enums.OrderStatus;
import platform.eshop.plaza.orderservice.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> myOrders(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatus status,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(orderService.listForBuyer(userId, q, status));
    }

    @GetMapping("/seller")
    public ResponseEntity<List<OrderResponseDTO>> ordersReceived(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatus status,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(orderService.listForSeller(userId, q, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrder(
            @PathVariable String id,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(orderService.getOrder(id, userId));
    }

    @PostMapping("/checkout")
    public ResponseEntity<List<OrderResponseDTO>> checkout(
            @Valid @RequestBody CheckoutRequestDTO request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.checkout(userId, request));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponseDTO> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrderStatusRequestDTO request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(orderService.updateStatus(id, userId, request.getStatus()));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponseDTO> cancel(
            @PathVariable String id,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(orderService.cancel(id, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable String id,
            @AuthenticationPrincipal String userId) {
        orderService.remove(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/redo")
    public ResponseEntity<OrderResponseDTO> redo(
            @PathVariable String id,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.redo(id, userId));
    }
}
