package platform.eshop.plaza.orderservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void round_removesFloatingPointNoise() {
        assertThat(Money.round(0.1 * 3)).isEqualTo(0.3);
    }

    @Test
    void round_roundsHalfUpToTwoDecimals() {
        assertThat(Money.round(2.675)).isEqualTo(2.68);
        assertThat(Money.round(2.674)).isEqualTo(2.67);
    }

    @Test
    void round_keepsWholeAmounts() {
        assertThat(Money.round(25.0)).isEqualTo(25.0);
        assertThat(Money.round(0)).isZero();
    }
}
