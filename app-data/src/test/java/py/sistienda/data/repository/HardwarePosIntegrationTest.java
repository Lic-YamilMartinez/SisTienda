package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.service.CodigoBarrasService;
import py.sistienda.core.service.ConfiguracionPosService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HardwarePosIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void persisteCodigoPluYConfiguracionPos() {
        var factory = new SqliteConnectionFactory(tempDir.resolve("hardware-pos.db"));
        new DatabaseInitializer(factory).initialize();

        var codigoService = new CodigoBarrasService();
        var productoRepository = new SqliteProductoRepository(factory);
        var productoService = new ProductoService(productoRepository, codigoService);

        var unidad = productoService.crear("Galletitas", null, null, UnidadMedida.UN, 8000, 5000,
                null, null);
        assertNotNull(unidad.codigoBarras());
        assertTrue(codigoService.esEan13Valido(unidad.codigoBarras()));
        assertEquals(unidad.id(), productoRepository.findByBarcode(unidad.codigoBarras()).orElseThrow().id());

        var pesado = productoService.crear("Queso Paraguay", null, null, UnidadMedida.KG, 55000, 40000,
                null, null);
        assertNotNull(pesado.pluBalanza());
        assertEquals(pesado.id(), productoRepository.findByPlu(pesado.pluBalanza()).orElseThrow().id());

        var configService = new ConfiguracionPosService(new SqliteConfiguracionPosRepository(factory));
        var saved = configService.guardar("21", 58, 50, true);
        var loaded = configService.obtener();
        assertEquals(saved, loaded);
        assertEquals("21", loaded.prefijoPeso());
        assertEquals(58, loaded.anchoTicketMm());
        assertEquals(50, loaded.anchoEtiquetaMm());
        assertTrue(loaded.imprimirTicketAutomatico());
    }

    @Test
    void impideCodigoYPluDuplicados() {
        var factory = new SqliteConnectionFactory(tempDir.resolve("duplicados.db"));
        new DatabaseInitializer(factory).initialize();
        var service = new ProductoService(new SqliteProductoRepository(factory), new CodigoBarrasService());

        service.crear("Producto A", null, null, UnidadMedida.UN, 1000, 500, "ABC123", null);
        RuntimeException codigoDuplicado = assertThrows(RuntimeException.class, () ->
                service.crear("Producto B", null, null, UnidadMedida.UN, 1200, 600, "ABC123", null));
        assertTrue(rootMessage(codigoDuplicado).toLowerCase().contains("código de barras"));

        service.crear("Pesable A", null, null, UnidadMedida.KG, 10000, 5000, null, 77);
        RuntimeException pluDuplicado = assertThrows(RuntimeException.class, () ->
                service.crear("Pesable B", null, null, UnidadMedida.KG, 12000, 6000, null, 77));
        assertTrue(rootMessage(pluDuplicado).toLowerCase().contains("plu"));
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause().getMessage() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }
}
