package py.sistienda.ui.catalogo;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductoImportFileParserTest {

    @TempDir
    Path tempDir;

    @Test
    void leeCsvConSeparadorPuntoYComa() throws Exception {
        Path file = tempDir.resolve("productos.csv");
        Files.writeString(file, """
                Nombre;Categoria;Unidad;Costo;PrecioVenta;StockInicial;StockMinimo;StockIdeal;CodigoBarras;PLUBalanza
                Yerba 1kg;Alimentos;UN;8000;10000;5;2;10;ABC123;
                """, StandardCharsets.UTF_8);

        var rows = ProductoImportFileParser.leer(file);
        assertEquals(1, rows.size());
        assertEquals("Yerba 1kg", rows.getFirst().nombre());
        assertEquals("UN", rows.getFirst().unidad());
        assertEquals("10", rows.getFirst().stockIdeal());
        assertEquals("ABC123", rows.getFirst().codigoBarras());
    }

    @Test
    void leeExcelConAliasDeEncabezados() throws Exception {
        Path file = tempDir.resolve("productos.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Productos");
            var header = sheet.createRow(0);
            String[] names = {"Producto", "Rubro", "UnidadMedida", "Costo", "Precio", "Stock", "Minimo", "Ideal", "Barcode", "PLU"};
            for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Carne molida");
            row.createCell(1).setCellValue("Carnes");
            row.createCell(2).setCellValue("KG");
            row.createCell(3).setCellValue(35000);
            row.createCell(4).setCellValue(45000);
            row.createCell(5).setCellValue(2.5);
            row.createCell(6).setCellValue(1);
            row.createCell(7).setCellValue(5);
            try (var out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        var rows = ProductoImportFileParser.leer(file);
        assertEquals(1, rows.size());
        assertEquals("Carne molida", rows.getFirst().nombre());
        assertEquals("KG", rows.getFirst().unidad());
        assertEquals("2.5", rows.getFirst().stockInicial());
        assertEquals("5", rows.getFirst().stockIdeal());
    }
}
