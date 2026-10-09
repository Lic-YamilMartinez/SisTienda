package py.sistienda.ui.fiado;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ImportacionClienteSaldoFila;

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

final class ClienteSaldoImportFileParser {

    private static final List<String> HEADERS = List.of(
            "Nombre", "Documento", "Telefono", "Direccion",
            "SaldoInicial", "FechaReferencia", "Referencia", "Observacion"
    );

    private ClienteSaldoImportFileParser() {
    }

    static List<ImportacionClienteSaldoFila> leer(Path path) {
        if (path == null) throw new ValidationException("Seleccioná un archivo para migrar.");
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) return leerExcel(path);
            if (name.endsWith(".csv") || name.endsWith(".txt")) return leerCsv(path);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException(
                    "No pudimos leer el archivo. Verificá que no esté dañado ni abierto de forma exclusiva."
            );
        }
        throw new ValidationException("Formato no compatible. Usá Excel (.xlsx/.xls) o CSV (.csv).");
    }

    static void crearPlantilla(Path path) {
        if (path == null) return;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Clientes y saldos");
            var headerStyle = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, switch (i) {
                    case 0 -> 8500;
                    case 3, 7 -> 10000;
                    case 6 -> 6500;
                    default -> 5000;
                });
            }
            sheet.createFreezePane(0, 1);

            Row example = sheet.createRow(1);
            String[] sample = {
                    "Maria Gonzalez", "1234567", "0981123456", "Barrio Centro",
                    "120000", "08/10/2026", "Cuaderno anterior", "Deuda previa a SisTienda"
            };
            for (int i = 0; i < sample.length; i++) example.createCell(i).setCellValue(sample[i]);

            Sheet ayuda = workbook.createSheet("Ayuda");
            String[][] rows = {
                    {"Campo", "Cómo cargarlo", "Ejemplo"},
                    {"Nombre", "Obligatorio", "Maria Gonzalez"},
                    {"Documento", "Opcional. Si ya existe, SisTienda vincula ese cliente", "1234567"},
                    {"Telefono", "Opcional. Ayuda a reconocer clientes sin documento", "0981123456"},
                    {"Direccion", "Opcional", "Barrio Centro"},
                    {"SaldoInicial", "Obligatorio. Deuda anterior a SisTienda, mayor a 0", "120000"},
                    {"FechaReferencia", "Opcional; vacío = fecha de migración. Formato dd/mm/aaaa", "08/10/2026"},
                    {"Referencia", "Opcional", "Cuaderno anterior"},
                    {"Observacion", "Opcional", "Saldo confirmado con la clienta"}
            };
            for (int r = 0; r < rows.length; r++) {
                Row row = ayuda.createRow(r);
                for (int col = 0; col < rows[r].length; col++) {
                    Cell cell = row.createCell(col);
                    cell.setCellValue(rows[r][col]);
                    if (r == 0) cell.setCellStyle(headerStyle);
                }
            }
            ayuda.setColumnWidth(0, 5200);
            ayuda.setColumnWidth(1, 17000);
            ayuda.setColumnWidth(2, 9000);
            ayuda.createFreezePane(0, 1);

            try (var output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear la plantilla de migración.", e);
        }
    }

    private static List<ImportacionClienteSaldoFila> leerExcel(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new ValidationException("El Excel no contiene hojas.");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new ValidationException("El Excel no contiene encabezados.");
            DataFormatter formatter = new DataFormatter(new Locale("es", "PY"));
            Map<String, Integer> columns = mapearEncabezados(celdas(header, formatter));
            validarEncabezados(columns);

            List<ImportacionClienteSaldoFila> filas = new ArrayList<>();
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
            if (filas.isEmpty()) throw new ValidationException("El archivo no contiene clientes debajo de los encabezados.");
            return List.copyOf(filas);
        }
    }

    private static List<ImportacionClienteSaldoFila> leerCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) throw new ValidationException("El CSV está vacío.");
            char delimiter = detectarDelimitador(headerLine);
            Map<String, Integer> columns = mapearEncabezados(parseCsvLine(headerLine, delimiter));
            validarEncabezados(columns);

            List<ImportacionClienteSaldoFila> filas = new ArrayList<>();
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
            if (filas.isEmpty()) throw new ValidationException("El archivo no contiene clientes debajo de los encabezados.");
            return List.copyOf(filas);
        }
    }

    private static ImportacionClienteSaldoFila toFila(int row, Map<String, String> values) {
        return new ImportacionClienteSaldoFila(
                row,
                value(values, "nombre"),
                value(values, "documento"),
                value(values, "telefono"),
                value(values, "direccion"),
                value(values, "saldo_inicial"),
                value(values, "fecha_referencia"),
                value(values, "referencia"),
                value(values, "observacion")
        );
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
        if (!columns.containsKey("saldo_inicial")) missing.add("SaldoInicial");
        if (!missing.isEmpty()) {
            throw new ValidationException(
                    "Faltan columnas obligatorias: " + String.join(", ", missing)
                            + ". Podés descargar la plantilla de SisTienda."
            );
        }
    }

    private static String value(Map<String, String> values, String key) {
        return values.getOrDefault(key, "");
    }

    private static String canonicalHeader(String value) {
        String key = normalize(value).replace("_", "");
        return switch (key) {
            case "nombre", "cliente", "nombrecliente" -> "nombre";
            case "documento", "cedula", "ci", "ruc", "documentoruc" -> "documento";
            case "telefono", "celular", "contacto" -> "telefono";
            case "direccion", "domicilio" -> "direccion";
            case "saldoinicial", "deuda", "deudainicial", "saldo" -> "saldo_inicial";
            case "fechareferencia", "fecha", "fechadeuda" -> "fecha_referencia";
            case "referencia", "origen" -> "referencia";
            case "observacion", "observaciones", "nota" -> "observacion";
            default -> null;
        };
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9_]", "");
    }

    private static char detectarDelimitador(String line) {
        int commas = contarFueraDeComillas(line, ',');
        int semicolons = contarFueraDeComillas(line, ';');
        int tabs = contarFueraDeComillas(line, '	');
        if (tabs >= commas && tabs >= semicolons && tabs > 0) return '	';
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
