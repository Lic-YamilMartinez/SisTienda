package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ImportacionProductoEntrada;
import py.sistienda.core.model.ImportacionProductoFila;
import py.sistienda.core.model.ImportacionProductoResultado;
import py.sistienda.core.model.ImportacionProductoValidacion;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.ImportacionProductoRepository;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ImportacionProductoService {
    private static final double EPSILON = 0.000001d;

    private final ImportacionProductoRepository repository;
    private final AutorizacionService autorizacionService;

    public ImportacionProductoService(ImportacionProductoRepository repository,
                                      AutorizacionService autorizacionService) {
        this.repository = Objects.requireNonNull(repository);
        this.autorizacionService = Objects.requireNonNull(autorizacionService);
    }

    public List<ImportacionProductoValidacion> validar(Usuario usuario, List<ImportacionProductoFila> filas) {
        exigirPermiso(usuario);
        if (filas == null || filas.isEmpty()) {
            throw new ValidationException("El archivo no contiene productos para importar.");
        }
        if (filas.size() > 10_000) {
            throw new ValidationException("La importación admite hasta 10.000 productos por archivo.");
        }

        List<ImportacionProductoValidacion> preliminar = filas.stream().map(this::validarFila).toList();
        Map<String, Integer> codigos = new HashMap<>();
        Map<Integer, Integer> plus = new HashMap<>();
        for (ImportacionProductoValidacion item : preliminar) {
            if (item.entrada() == null) continue;
            String codigo = item.entrada().codigoBarras();
            if (codigo != null) codigos.merge(codigo.toLowerCase(Locale.ROOT), 1, Integer::sum);
            Integer plu = item.entrada().pluBalanza();
            if (plu != null) plus.merge(plu, 1, Integer::sum);
        }

        List<String> codigosConsulta = preliminar.stream()
                .filter(item -> item.entrada() != null && item.entrada().codigoBarras() != null)
                .map(item -> item.entrada().codigoBarras()).distinct().toList();
        List<Integer> plusConsulta = preliminar.stream()
                .filter(item -> item.entrada() != null && item.entrada().pluBalanza() != null)
                .map(item -> item.entrada().pluBalanza()).distinct().toList();
        Set<String> codigosExistentes = repository.codigosExistentes(codigosConsulta);
        Set<Integer> plusExistentes = repository.plusExistentes(plusConsulta);
        Set<String> codigosExistentesLower = new HashSet<>();
        codigosExistentes.forEach(value -> codigosExistentesLower.add(value.toLowerCase(Locale.ROOT)));

        List<ImportacionProductoValidacion> resultado = new ArrayList<>();
        for (ImportacionProductoValidacion item : preliminar) {
            List<String> errores = new ArrayList<>(item.errores());
            ImportacionProductoEntrada entrada = item.entrada();
            if (entrada != null && entrada.codigoBarras() != null) {
                String key = entrada.codigoBarras().toLowerCase(Locale.ROOT);
                if (codigos.getOrDefault(key, 0) > 1) errores.add("Código de barras repetido dentro del archivo");
                if (codigosExistentesLower.contains(key)) errores.add("El código de barras ya existe en SisTienda");
            }
            if (entrada != null && entrada.pluBalanza() != null) {
                if (plus.getOrDefault(entrada.pluBalanza(), 0) > 1) errores.add("PLU repetido dentro del archivo");
                if (plusExistentes.contains(entrada.pluBalanza())) errores.add("El PLU ya existe en SisTienda");
            }
            resultado.add(new ImportacionProductoValidacion(
                    item.fila(), item.nombre(), entrada, List.copyOf(errores)));
        }
        return List.copyOf(resultado);
    }

    public ImportacionProductoResultado importar(Usuario usuario, List<ImportacionProductoValidacion> validaciones) {
        exigirPermiso(usuario);
        if (validaciones == null) throw new ValidationException("Primero revisá el archivo a importar.");
        List<ImportacionProductoEntrada> validas = validaciones.stream()
                .filter(ImportacionProductoValidacion::valida)
                .map(ImportacionProductoValidacion::entrada)
                .filter(Objects::nonNull)
                .toList();
        if (validas.isEmpty()) {
            throw new ValidationException("No hay filas válidas para importar.");
        }
        return repository.importar(validas, usuario.id());
    }

    private ImportacionProductoValidacion validarFila(ImportacionProductoFila fila) {
        List<String> errores = new ArrayList<>();
        String nombre = normalizar(fila.nombre());
        if (nombre == null) errores.add("Falta el nombre");
        else if (nombre.length() > 120) errores.add("El nombre supera 120 caracteres");

        String categoria = normalizar(fila.categoria());
        if (categoria != null && categoria.length() > 100) errores.add("La categoría es demasiado larga");

        UnidadMedida unidad = parseUnidad(fila.unidad(), errores);
        Double costo = parseNumero(fila.costo(), "costo", false, errores);
        Double precio = parseNumero(fila.precioVenta(), "precio de venta", true, errores);
        Double stock = parseNumero(fila.stockInicial(), "stock inicial", false, errores);
        Double minimo = parseNumero(fila.stockMinimo(), "stock mínimo", false, errores);
        Double ideal = parseNumero(fila.stockIdeal(), "stock ideal", false, errores);

        String codigo = normalizar(fila.codigoBarras());
        if (codigo != null && !codigo.matches("[A-Za-z0-9._-]{3,64}")) {
            errores.add("Código de barras inválido");
        }
        Integer plu = parsePlu(fila.pluBalanza(), errores);
        if (unidad == UnidadMedida.UN && plu != null) errores.add("Un producto por unidad no debe tener PLU de balanza");

        if (unidad != null && costo != null && precio != null && stock != null && minimo != null && ideal != null) {
            if (costo < 0 || precio < 0 || stock < 0 || minimo < 0 || ideal < 0) {
                errores.add("Costo, precio y existencias no pueden ser negativos");
            }
            if (ideal + EPSILON < minimo) errores.add("El stock ideal no puede ser menor al mínimo");
            if (unidad == UnidadMedida.UN
                    && (!esEntero(stock) || !esEntero(minimo) || !esEntero(ideal))) {
                errores.add("UN requiere stock inicial, mínimo e ideal enteros");
            }
        }

        ImportacionProductoEntrada entrada = null;
        if (nombre != null && unidad != null && costo != null && precio != null && stock != null && minimo != null && ideal != null) {
            entrada = new ImportacionProductoEntrada(
                    fila.fila(), nombre, categoria, unidad, costo, precio, stock, minimo, ideal, codigo, plu);
        }
        return new ImportacionProductoValidacion(fila.fila(), nombre == null ? "(sin nombre)" : nombre,
                entrada, List.copyOf(errores));
    }

    private UnidadMedida parseUnidad(String value, List<String> errores) {
        String normalized = normalizar(value);
        if (normalized == null) {
            errores.add("Falta la unidad (UN o KG)");
            return null;
        }
        String key = normalized.toUpperCase(Locale.ROOT);
        if (Set.of("UN", "U", "UNIDAD", "UNIDADES").contains(key)) return UnidadMedida.UN;
        if (Set.of("KG", "KILO", "KILOS", "KILOGRAMO", "KILOGRAMOS").contains(key)) return UnidadMedida.KG;
        errores.add("Unidad inválida: usá UN o KG");
        return null;
    }

    private Double parseNumero(String value, String campo, boolean requerido, List<String> errores) {
        String normalized = normalizar(value);
        if (normalized == null) {
            if (requerido) errores.add("Falta el " + campo);
            return requerido ? null : 0d;
        }
        normalized = normalized.replace("Gs.", "").replace("Gs", "").replace("₲", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            double result = Double.parseDouble(normalized);
            if (!Double.isFinite(result)) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) {
            errores.add("Revisá el " + campo);
            return null;
        }
    }

    private Integer parsePlu(String value, List<String> errores) {
        String normalized = normalizar(value);
        if (normalized == null) return null;
        normalized = normalized.replace(',', '.');
        try {
            double numeric = Double.parseDouble(normalized);
            if (!Double.isFinite(numeric) || !esEntero(numeric) || numeric < 0 || numeric > 99_999) {
                throw new NumberFormatException();
            }
            return (int) Math.rint(numeric);
        } catch (NumberFormatException e) {
            errores.add("PLU inválido: debe ser un entero entre 0 y 99999");
            return null;
        }
    }

    private boolean esEntero(double value) {
        return Math.abs(value - Math.rint(value)) <= EPSILON;
    }

    private String normalizar(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceAll("\\s+", " ");
    }

    private void exigirPermiso(Usuario usuario) {
        Objects.requireNonNull(usuario);
        autorizacionService.exigir(usuario, Permiso.CATALOGO_GESTIONAR);
        autorizacionService.exigir(usuario, Permiso.STOCK_GESTIONAR);
    }
}
