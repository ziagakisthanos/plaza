package platform.eshop.plaza.productservice.service;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import platform.eshop.plaza.productservice.dto.ProductResponseDTO;
import platform.eshop.plaza.productservice.dto.ProductSearchCriteria;
import platform.eshop.plaza.productservice.entity.Product;
import platform.eshop.plaza.productservice.repository.ProductRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceSearchTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ProductService productService;

    private Query searchAndCaptureQuery(ProductSearchCriteria criteria) {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(captor.capture(), eq(Product.class))).thenReturn(List.of());
        productService.searchProducts(criteria);
        return captor.getValue();
    }

    @Test
    void emptyCriteria_matchesEverything_newestFirst() {
        Query query = searchAndCaptureQuery(new ProductSearchCriteria());

        assertThat(query.getQueryObject()).isEmpty();
        assertThat(query.getSortObject()).isEqualTo(new Document("createdAt", -1));
    }

    @Test
    void blankTextAndCategory_areIgnored() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setQ("   ");
        criteria.setCategory("");

        Query query = searchAndCaptureQuery(criteria);

        assertThat(query.getQueryObject()).isEmpty();
    }

    @Test
    void text_searchesNameAndDescription_caseInsensitively() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setQ("  Laptop ");

        String json = searchAndCaptureQuery(criteria).getQueryObject().toJson();

        assertThat(json).contains("$or").contains("\"name\"").contains("\"description\"");
        assertThat(json).contains("\\\\QLaptop\\\\E").contains("\"options\": \"i\"");
    }

    @Test
    void text_isQuotedSoRegexCharactersAreLiteral() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setQ(".*");

        String json = searchAndCaptureQuery(criteria).getQueryObject().toJson();

        assertThat(json).contains("\\\\Q.*\\\\E");
    }

    @Test
    void category_matchesExactly() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setCategory(" Electronics ");

        String json = searchAndCaptureQuery(criteria).getQueryObject().toJson();

        assertThat(json).isEqualTo("{\"$and\": [{\"category\": \"Electronics\"}]}");
    }

    @Test
    void priceRange_usesBothBounds() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setMinPrice(10.0);
        criteria.setMaxPrice(50.5);

        String json = searchAndCaptureQuery(criteria).getQueryObject().toJson();

        assertThat(json).isEqualTo("{\"$and\": [{\"price\": {\"$gte\": 10.0, \"$lte\": 50.5}}]}");
    }

    @Test
    void minPriceOnly_hasNoUpperBound() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setMinPrice(10.0);

        String json = searchAndCaptureQuery(criteria).getQueryObject().toJson();

        assertThat(json).isEqualTo("{\"$and\": [{\"price\": {\"$gte\": 10.0}}]}");
    }

    @Test
    void maxPriceOnly_hasNoLowerBound() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setMaxPrice(50.0);

        String json = searchAndCaptureQuery(criteria).getQueryObject().toJson();

        assertThat(json).isEqualTo("{\"$and\": [{\"price\": {\"$lte\": 50.0}}]}");
    }

    @Test
    void inStockTrue_requiresPositiveQuantity() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setInStock(true);

        String json = searchAndCaptureQuery(criteria).getQueryObject().toJson();

        assertThat(json).isEqualTo("{\"$and\": [{\"quantity\": {\"$gt\": 0}}]}");
    }

    @Test
    void inStockFalse_doesNotFilter() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setInStock(false);

        Query query = searchAndCaptureQuery(criteria);

        assertThat(query.getQueryObject()).isEmpty();
    }

    @Test
    void allFilters_areCombinedWithAnd() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setQ("lap");
        criteria.setCategory("Electronics");
        criteria.setMinPrice(1.0);
        criteria.setMaxPrice(2.0);
        criteria.setInStock(true);

        Query query = searchAndCaptureQuery(criteria);

        List<?> filters = (List<?>) query.getQueryObject().get("$and");
        assertThat(filters).hasSize(4);
    }

    @Test
    void sortPriceAsc_ordersByPriceAscending() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setSort("price_asc");

        Query query = searchAndCaptureQuery(criteria);

        assertThat(query.getSortObject()).isEqualTo(new Document("price", 1));
    }

    @Test
    void sortPriceDesc_ordersByPriceDescending() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setSort("price_desc");

        Query query = searchAndCaptureQuery(criteria);

        assertThat(query.getSortObject()).isEqualTo(new Document("price", -1));
    }

    @Test
    void sortNewest_ordersByCreationTimeDescending() {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setSort("newest");

        Query query = searchAndCaptureQuery(criteria);

        assertThat(query.getSortObject()).isEqualTo(new Document("createdAt", -1));
    }

    @Test
    void searchProducts_mapsResultsToResponseDtos() {
        Instant created = Instant.parse("2026-02-02T08:00:00Z");
        Product product = new Product("p1", "Laptop", "desc", 999.0, 3, "seller-1", "Electronics", created);
        when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));

        List<ProductResponseDTO> result = productService.searchProducts(new ProductSearchCriteria());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("p1");
        assertThat(result.get(0).getCategory()).isEqualTo("Electronics");
        assertThat(result.get(0).getCreatedAt()).isEqualTo(created);
    }

    @Test
    void getCategories_dropsMissingAndBlankValues_andSorts() {
        when(mongoTemplate.findDistinct(any(Query.class), eq("category"), eq(Product.class), eq(String.class)))
                .thenReturn(Arrays.asList("Toys", null, " ", "Books"));

        List<String> categories = productService.getCategories();

        assertThat(categories).containsExactly("Books", "Toys");
        verify(mongoTemplate).findDistinct(any(Query.class), eq("category"), eq(Product.class), eq(String.class));
    }
}
