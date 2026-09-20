package platform.zone01.orderservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import platform.zone01.orderservice.dto.ClientStatsDTO;
import platform.zone01.orderservice.dto.SellerStatsDTO;
import platform.zone01.orderservice.service.OrderStatsService;

@RestController
@RequestMapping("/orders/stats")
public class OrderStatsController {

    private final OrderStatsService orderStatsService;

    public OrderStatsController(OrderStatsService orderStatsService) {
        this.orderStatsService = orderStatsService;
    }

    @GetMapping("/client")
    public ResponseEntity<ClientStatsDTO> clientStats(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(orderStatsService.clientStats(userId));
    }

    @GetMapping("/seller")
    public ResponseEntity<SellerStatsDTO> sellerStats(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(orderStatsService.sellerStats(userId));
    }
}
