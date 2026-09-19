package py.sistienda.ui.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewportPolicyTest {

    @Test
    void keepsMainWindowInside1366x768VisibleArea() {
        var size = ViewportPolicy.window(1366, 728, 1360, 820, 960, 580, 16);

        assertEquals(1350d, size.width());
        assertEquals(712d, size.height());
        assertTrue(size.minimumWidth() <= size.width());
        assertTrue(size.minimumHeight() <= size.height());
    }

    @Test
    void keepsMinimumsPossibleUnder125PercentScaling() {
        var size = ViewportPolicy.window(1093, 614, 1360, 820, 960, 580, 16);

        assertEquals(1077d, size.width());
        assertEquals(598d, size.height());
        assertEquals(960d, size.minimumWidth());
        assertEquals(580d, size.minimumHeight());
    }

    @Test
    void neverCreatesImpossibleMinimumOnSmallDesktop() {
        var size = ViewportPolicy.window(900, 540, 1180, 760, 980, 680, 16);

        assertEquals(884d, size.width());
        assertEquals(524d, size.height());
        assertEquals(884d, size.minimumWidth());
        assertEquals(524d, size.minimumHeight());
    }
}
