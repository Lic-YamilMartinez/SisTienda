package py.sistienda.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {

    private MoneyMath() {
    }

    /**
     * SisTienda opera en guaraníes, una moneda sin fracción operativa.
     * Toda cifra monetaria calculada se cuantiza a guaraní entero con HALF_UP.
     */
    public static double guaranies(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("El importe monetario no es válido.");
        }
        return BigDecimal.valueOf(value)
                .setScale(0, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static double subtotal(double unitValue, double quantity) {
        if (!Double.isFinite(unitValue) || !Double.isFinite(quantity)) {
            throw new IllegalArgumentException("El cálculo monetario no es válido.");
        }
        return guaranies(unitValue * quantity);
    }
}
