package py.sistienda.data.qa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimercadoDemoSeederTest {

    @TempDir
    Path tempDir;

    @Test
    void cargaCatalogoDemoUnaSolaVezYDejaCasosDeReposicion() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("demo.db"));

        var first = MinimercadoDemoSeeder.seed(factory);
        assertEquals(38, first.created());
        assertEquals(0, first.existing());
        assertEquals(36, first.stockSeeded());
        assertEquals(38, first.totalProducts());

        var second = MinimercadoDemoSeeder.seed(factory);
        assertEquals(0, second.created());
        assertEquals(38, second.existing());
        assertEquals(0, second.stockSeeded());

        try (var connection = factory.open();
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT COUNT(*) AS total
                    FROM producto
                    WHERE codigo_barras LIKE 'QA%' OR plu_balanza BETWEEN 91001 AND 91007
                    """)) {
                assertTrue(result.next());
                assertEquals(38, result.getInt("total"));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*) AS total
                    FROM producto
                    WHERE stock_ideal > 0 AND stock_actual <= stock_minimo
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getInt("total") >= 5,
                        "El demo debe dejar productos sin stock o bajo mínimo para probar reposición.");
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*) AS total
                    FROM producto
                    WHERE unidad_medida = 'KG' AND stock_actual > 0 AND stock_actual <> CAST(stock_actual AS INTEGER)
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getInt("total") >= 3,
                        "El demo debe incluir cantidades decimales para probar venta e inventario por kg.");
            }
        }
    }
}
