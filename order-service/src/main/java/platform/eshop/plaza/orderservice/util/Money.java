package platform.eshop.plaza.orderservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private Money() {
    }

    public static double round(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
