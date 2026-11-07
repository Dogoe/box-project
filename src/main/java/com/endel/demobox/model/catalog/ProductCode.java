package com.endel.demobox.model.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Formatting for {@link Product#getCode()}: the short identifier shown in the kit editor's selectors,
 * built from the product's type, its main specs and its price, e.g. {@code INVERSOR-1000W-12V-120V-$3000}.
 * It is computed on the fly, never stored, so it always matches the product's current data.
 */
final class ProductCode {

    private ProductCode() {
    }

    /** A number without a useless ".0" and at most 2 decimals: 12 -> "12", 12.8 -> "12.8", 3000.50 -> "3000.5". */
    static String num(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String num(Double value) {
        return value == null ? null : num(value.doubleValue());
    }

    /** "$3000" (no thousands separators, decimals only when there are cents). */
    static String price(BigDecimal price) {
        BigDecimal value = price == null ? BigDecimal.ZERO : price;
        return "$" + value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** "1/0 AWG" -> "1/0AWG". */
    static String gauge(WireGauge gauge) {
        return gauge == null ? null : gauge.getLabel().replace(" ", "");
    }

    static String join(String type, List<String> specs, BigDecimal price) {
        StringBuilder code = new StringBuilder(type);
        specs.stream().filter(s -> s != null && !s.isBlank()).forEach(s -> code.append('-').append(s));
        return code.append('-').append(price(price)).toString();
    }
}
