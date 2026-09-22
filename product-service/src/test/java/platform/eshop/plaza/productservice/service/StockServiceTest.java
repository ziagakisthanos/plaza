package platform.eshop.plaza.productservice.service;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import platform.eshop.plaza.productservice.dto.ProductResponseDTO;
import platform.eshop.plaza.productservice.dto.StockItemDTO;
import platform.eshop.plaza.productservice.entity.Product;
import platform.eshop.plaza.productservice.exception.InsufficientStockException;
import platform.eshop.plaza.productservice.exception.ProductNotFoundException;
import platform.eshop.plaza.productservice.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private StockService stockService;

    private static UpdateResult modified(long count) {
        return UpdateResult.acknowledged(count, count, null);
    }

    private static Product product(String id, String name, int quantity) {
        return new Product(id, name, "desc", 10.0, quantity, "seller-1", "Books", null);
    }

    private ArgumentCaptor<Query> queries;
    private ArgumentCaptor<Update> updates;

    private void captureUpdates(int expectedCalls) {
        queries = ArgumentCaptor.forClass(Query.class);
        updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(expectedCalls))
                .updateFirst(queries.capture(), updates.capture(), eq(Product.class));
    }

    @Test
    void reserve_takesEachItemWithAConditionalDecrement() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(modified(1));
        when(productRepository.findAllById(List.of("p1", "p2")))
                .thenReturn(List.of(product("p1", "Book", 3), product("p2", "Pen", 8)));

        List<ProductResponseDTO> result = stockService.reserve(
                List.of(new StockItemDTO("p1", 2), new StockItemDTO("p2", 1)));

        captureUpdates(2);
        assertThat(queries.getAllValues().get(0).getQueryObject().toJson())
                .isEqualTo("{\"id\": \"p1\", \"quantity\": {\"$gte\": 2}}");
        assertThat(updates.getAllValues().get(0).getUpdateObject().toJson())
                .isEqualTo("{\"$inc\": {\"quantity\": -2}}");
        assertThat(updates.getAllValues().get(1).getUpdateObject().toJson())
                .isEqualTo("{\"$inc\": {\"quantity\": -1}}");
        assertThat(result).extracting(ProductResponseDTO::getId).containsExactly("p1", "p2");
    }

    @Test
    void reserve_mergesTheSameProductListedTwice() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(modified(1));
        when(productRepository.findAllById(List.of("p1"))).thenReturn(List.of(product("p1", "Book", 1)));

        stockService.reserve(List.of(new StockItemDTO("p1", 1), new StockItemDTO("p1", 2)));

        captureUpdates(1);
        assertThat(updates.getValue().getUpdateObject().toJson()).isEqualTo("{\"$inc\": {\"quantity\": -3}}");
    }

    @Test
    void reserve_failsWithStockMessage_andGivesBackWhatWasAlreadyTaken() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(modified(1), modified(0), modified(1));
        when(productRepository.findById("p2")).thenReturn(Optional.of(product("p2", "Pen", 1)));

        assertThatThrownBy(() -> stockService.reserve(
                List.of(new StockItemDTO("p1", 2), new StockItemDTO("p2", 5))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Not enough stock for 'Pen': only 1 left");

        captureUpdates(3);
        assertThat(queries.getAllValues().get(2).getQueryObject().toJson()).isEqualTo("{\"id\": \"p1\"}");
        assertThat(updates.getAllValues().get(2).getUpdateObject().toJson())
                .isEqualTo("{\"$inc\": {\"quantity\": 2}}");
    }

    @Test
    void reserve_failsOnTheFirstItem_withoutGivingBackAnything() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(modified(0));
        when(productRepository.findById("p1")).thenReturn(Optional.of(product("p1", "Book", 0)));

        assertThatThrownBy(() -> stockService.reserve(List.of(new StockItemDTO("p1", 1))))
                .isInstanceOf(InsufficientStockException.class);

        captureUpdates(1);
    }

    @Test
    void reserve_throwsNotFound_whenTheProductIsGone_andGivesBackTheRest() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(modified(1), modified(0), modified(1));
        when(productRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.reserve(
                List.of(new StockItemDTO("p1", 1), new StockItemDTO("gone", 1))))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product with id: gone not found");

        captureUpdates(3);
    }

    @Test
    void release_addsTheQuantitiesBack() {
        stockService.release(List.of(new StockItemDTO("p1", 2), new StockItemDTO("p2", 4)));

        captureUpdates(2);
        assertThat(queries.getAllValues().get(0).getQueryObject().toJson()).isEqualTo("{\"id\": \"p1\"}");
        assertThat(updates.getAllValues().get(0).getUpdateObject().toJson())
                .isEqualTo("{\"$inc\": {\"quantity\": 2}}");
        assertThat(updates.getAllValues().get(1).getUpdateObject().toJson())
                .isEqualTo("{\"$inc\": {\"quantity\": 4}}");
    }

    @Test
    void release_mergesTheSameProductListedTwice() {
        stockService.release(List.of(new StockItemDTO("p1", 2), new StockItemDTO("p1", 3)));

        captureUpdates(1);
        assertThat(updates.getValue().getUpdateObject().toJson()).isEqualTo("{\"$inc\": {\"quantity\": 5}}");
    }

    @Test
    void lookup_returnsOnlyTheProductsThatStillExist() {
        when(productRepository.findAllById(List.of("p1", "deleted")))
                .thenReturn(List.of(product("p1", "Book", 3)));

        List<ProductResponseDTO> result = stockService.lookup(List.of("p1", "deleted"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Book");
        assertThat(result.get(0).getUserId()).isEqualTo("seller-1");
    }
}
