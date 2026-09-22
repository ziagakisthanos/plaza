package platform.eshop.plaza.productservice.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validRequest_hasNoViolations() {
        ProductRequestDTO request = new ProductRequestDTO("Laptop", "desc", "Electronics", 999.0, 5);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void blankCategory_isRejected() {
        ProductRequestDTO request = new ProductRequestDTO("Laptop", "desc", "  ", 999.0, 5);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("category");
    }

    @Test
    void missingCategory_isRejected() {
        ProductRequestDTO request = new ProductRequestDTO("Laptop", "desc", null, 999.0, 5);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("Please choose a category");
    }

    @Test
    void tooLongCategory_isRejected() {
        ProductRequestDTO request = new ProductRequestDTO("Laptop", "desc", "x".repeat(51), 999.0, 5);

        assertThat(validator.validate(request)).hasSize(1);
    }
}
