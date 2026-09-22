package platform.eshop.plaza.productservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import platform.eshop.plaza.productservice.dto.LookupRequestDTO;
import platform.eshop.plaza.productservice.dto.ProductResponseDTO;
import platform.eshop.plaza.productservice.dto.StockRequestDTO;
import platform.eshop.plaza.productservice.service.StockService;

import java.util.List;

@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final StockService stockService;

    public InternalProductController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/lookup")
    public ResponseEntity<List<ProductResponseDTO>> lookup(@Valid @RequestBody LookupRequestDTO request) {
        return ResponseEntity.ok(stockService.lookup(request.getIds()));
    }

    @PostMapping("/reserve")
    public ResponseEntity<List<ProductResponseDTO>> reserve(@Valid @RequestBody StockRequestDTO request) {
        return ResponseEntity.ok(stockService.reserve(request.getItems()));
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@Valid @RequestBody StockRequestDTO request) {
        stockService.release(request.getItems());
        return ResponseEntity.noContent().build();
    }
}
