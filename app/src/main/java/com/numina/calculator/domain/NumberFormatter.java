package com.numina.calculator.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class NumberFormatter {
    private static final MathContext DISPLAY_PRECISION = new MathContext(12, RoundingMode.HALF_UP);

    private NumberFormatter() {
    }

    public static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Only finite numbers can be formatted");
        }
        if (value == 0.0d) {
            return "0";
        }

        BigDecimal rounded = BigDecimal.valueOf(value).round(DISPLAY_PRECISION).stripTrailingZeros();
        double magnitude = Math.abs(value);
        if (magnitude >= 1.0e12 || magnitude < 1.0e-9) {
            String engineering = rounded.toEngineeringString();
            int exponentMarker = Math.max(engineering.indexOf('E'), engineering.indexOf('e'));
            if (exponentMarker >= 0) {
                return engineering.substring(0, exponentMarker) + "e" +
                    engineering.substring(exponentMarker + 1).replace("+", "");
            }
            return engineering;
        }
        return rounded.toPlainString();
    }
}
