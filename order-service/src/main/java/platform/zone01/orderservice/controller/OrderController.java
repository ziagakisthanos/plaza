package platform.zone01.orderservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import platform.zone01.orderservice.dto.CheckoutRequestDTO;
import platform.zone01.orderservice.dto.OrderResponseDTO;
import platform.zone01.orderservice.enums.OrderStatus;
import platform.zone01.orderservice.service.OrderService;

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
}
