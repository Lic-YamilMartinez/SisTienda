package py.sistienda.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyMathTest {

    @Test
    void redondeaSubtotalesDeKgAGuaraniEntero() {
        assertEquals(3450d, MoneyMath.subtotal(9999d, 0.345d));
        assertEquals(2070d, MoneyMath.subtotal(6000d, 0.345d));
    }

    @Test
    void usaHalfUpTambienParaReversiones() {
        assertEquals(-3450d, MoneyMath.guaranies(-3449.655d));
    }

    @Test
    void rechazaImportesNoFinitos() {
        assertThrows(IllegalArgumentException.class, () -> MoneyMath.guaranies(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MoneyMath.guaranies(Double.POSITIVE_INFINITY));
    }
}
