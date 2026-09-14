package py.sistienda.ui.catalogo;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ImportacionProductoFila;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ProductoImportFileParser {
    private static final List<String> HEADERS = List.of(
            "Nombre", "Categoria", "Unidad", "Costo", "PrecioVenta",
            "StockInicial", "StockMinimo", "StockIdeal", "CodigoBarras", "PLUBalanza"
    );

    private ProductoImportFileParser() {
    }

    static List<ImportacionProductoFila> leer(Path path) {
        if (path == null) throw new ValidationException("Seleccioná un archivo para importar.");
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) return leerExcel(path);
            if (name.endsWith(".csv") || name.endsWith(".txt")) return leerCsv(path);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("No pudimos leer el archivo. Verificá que no esté dañado ni abierto de forma exclusiva.");
        }
        throw new ValidationException("Formato no compatible. Usá Excel (.xlsx/.xls) o CSV (.csv).");
    }

    static void crearPlantilla(Path path) {
        if (path == null) return;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet productos = workbook.createSheet("Productos");
            var headerStyle = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = productos.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(headerStyle);
                productos.setColumnWidth(i, switch (i) {
                    case 0 -> 8500;
                    case 1 -> 5000;
                    case 8 -> 5200;
                    default -> 3800;
                });
            }
            productos.createFreezePane(0, 1);

            Sheet ayuda = workbook.createSheet("Ayuda");
            String[][] rows = {
                    {"Campo", "Cómo cargarlo", "Ejemplo"},
                    {"Nombre", "Obligatorio", "Coca Cola 2L"},
                    {"Categoria", "Opcional. Si no existe, SisTienda la crea", "Bebidas"},
                    {"Unidad", "Obligatorio: UN o KG", "UN"},
                    {"Costo", "Opcional; vacío = 0", "9000"},
                    {"PrecioVenta", "Obligatorio", "12000"},
                    {"StockInicial", "Opcional; vacío = 0", "24"},
                    {"StockMinimo", "Nivel que dispara la alerta", "6"},
                    {"StockIdeal", "Cantidad objetivo para sugerir compra. 0 desactiva alerta", "24"},
                    {"CodigoBarras", "Opcional en UN; SisTienda genera uno si queda vacío", "7841234567890"},
                    {"PLUBalanza", "Opcional en KG; SisTienda asigna uno si queda vacío", "105"}
            };
            for (int r = 0; r < rows.length; r++) {
                Row row = ayuda.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(rows[r][c]);
                    if (r == 0) cell.setCellStyle(headerStyle);
                }
            }
            ayuda.setColumnWidth(0, 5200);
            ayuda.setColumnWidth(1, 15000);
            ayuda.setColumnWidth(2, 7000);
            ayuda.createFreezePane(0, 1);

            try (var output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear la plantilla de importación.", e);
        }
    }

    private static List<ImportacionProductoFila> leerExcel(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new ValidationException("El Excel no contiene hojas.");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new ValidationException("El Excel no contiene encabezados.");
            DataFormatter formatter = new DataFormatter(Locale.US);
            Map<String, Integer> columns = mapearEncabezados(celdas(header, formatter));
            validarEncabezados(columns);

            List<ImportacionProductoFila> filas = new ArrayList<>();
            for (int rowIndex = header.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, Integer> entry : columns.entrySet()) {
                    Cell cell = row.getCell(entry.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.put(entry.getKey(), cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                if (values.values().stream().allMatch(String::isBlank)) continue;
                filas.add(toFila(rowIndex + 1, values));
            }
            if (filas.isEmpty()) throw new ValidationException("El archivo no contiene productos debajo de los encabezados.");
            return List.copyOf(filas);
        }
    }

    private static List<ImportacionProductoFila> leerCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) throw new ValidationException("El CSV está vacío.");
            char delimiter = detectarDelimitador(headerLine);
            List<String> headers = parseCsvLine(headerLine, delimiter);
            Map<String, Integer> columns = mapearEncabezados(headers);
            validarEncabezados(columns);

            List<ImportacionProductoFila> filas = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) continue;
                List<String> cells = parseCsvLine(line, delimiter);
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, Integer> entry : columns.entrySet()) {
                    values.put(entry.getKey(), entry.getValue() < cells.size() ? cells.get(entry.getValue()).trim() : "");
                }
                if (values.values().stream().allMatch(String::isBlank)) continue;
                filas.add(toFila(rowNumber, values));
            }
            if (filas.isEmpty()) throw new ValidationException("El archivo no contiene productos debajo de los encabezados.");
            return List.copyOf(filas);
        }
    }

    private static List<String> celdas(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        int last = Math.max(0, row.getLastCellNum());
        for (int i = 0; i < last; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(cell == null ? "" : formatter.formatCellValue(cell));
        }
        return values;
    }

    private static Map<String, Integer> mapearEncabezados(List<String> headers) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String canonical = canonicalHeader(headers.get(i));
            if (canonical != null) result.putIfAbsent(canonical, i);
        }
        return result;
    }

    private static void validarEncabezados(Map<String, Integer> columns) {
        List<String> missing = new ArrayList<>();
        if (!columns.containsKey("nombre")) missing.add("Nombre");
        if (!columns.containsKey("unidad")) missing.add("Unidad");
        if (!columns.containsKey("precio_venta")) missing.add("PrecioVenta");
        if (!missing.isEmpty()) {
            throw new ValidationException("Faltan columnas obligatorias: " + String.join(", ", missing)
                    + ". Podés descargar la plantilla de SisTienda.");
        }
    }

    private static ImportacionProductoFila toFila(int row, Map<String, String> values) {
        return new ImportacionProductoFila(
                row,
                value(values, "nombre"), value(values, "categoria"), value(values, "unidad"),
                value(values, "costo"), value(values, "precio_venta"), value(values, "stock_inicial"),
                value(values, "stock_minimo"), value(values, "stock_ideal"),
                value(values, "codigo_barras"), value(values, "plu_balanza")
        );
    }

    private static String value(Map<String, String> values, String key) {
        return values.getOrDefault(key, "");
    }

    private static String canonicalHeader(String value) {
        String key = normalize(value).replace("_", "");
        return switch (key) {
            case "nombre", "producto", "descripcion", "descripcionproducto" -> "nombre";
            case "categoria", "rubro" -> "categoria";
            case "unidad", "unidadmedida", "medida" -> "unidad";
            case "costo", "costounitario" -> "costo";
            case "precio", "precioventa", "ventaprecio" -> "precio_venta";
            case "stock", "stockinicial", "existencia", "existencias" -> "stock_inicial";
            case "stockminimo", "minimo", "puntoreorden" -> "stock_minimo";
            case "stockideal", "ideal", "stockobjetivo" -> "stock_ideal";
            case "codigo", "codigobarras", "barcode", "ean" -> "codigo_barras";
            case "plu", "plubalanza" -> "plu_balanza";
            default -> null;
        };
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9_]", "");
    }

    private static char detectarDelimitador(String line) {
        int commas = contarFueraDeComillas(line, ',');
        int semicolons = contarFueraDeComillas(line, ';');
        int tabs = contarFueraDeComillas(line, '\t');
        if (tabs >= commas && tabs >= semicolons && tabs > 0) return '\t';
        return semicolons > commas ? ';' : ',';
    }

    private static int contarFueraDeComillas(String line, char delimiter) {
        boolean quoted = false;
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (!quoted && c == delimiter) count++;
        }
        return count;
    }

    private static List<String> parseCsvLine(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}
