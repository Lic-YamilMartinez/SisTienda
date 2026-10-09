package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ImportacionClienteSaldoEntrada;
import py.sistienda.core.model.ImportacionClienteSaldoFila;
import py.sistienda.core.model.ImportacionClienteSaldoResultado;
import py.sistienda.core.model.ImportacionClienteSaldoValidacion;
import py.sistienda.core.model.MigracionClienteCoincidencia;
import py.sistienda.core.model.SaldoInicialClienteResultado;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.MigracionClienteRepository;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;
import py.sistienda.core.util.MoneyMath;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MigracionClienteService {

    private static final double EPSILON = 0.000001d;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private final MigracionClienteRepository repository;
    private final AutorizacionService autorizacionService;

    public MigracionClienteService(MigracionClienteRepository repository,
                                   AutorizacionService autorizacionService) {
        this.repository = Objects.requireNonNull(repository);
        this.autorizacionService = Objects.requireNonNull(autorizacionService);
    }

    public SaldoInicialClienteResultado registrarSaldoInicial(
            Usuario usuario,
            Cliente cliente,
            double monto,
            LocalDate fechaReferencia,
            String referencia,
            String observacion
    ) {
        exigirPermiso(usuario);
        Objects.requireNonNull(cliente);
        if (!cliente.activo()) throw new ValidationException("El cliente seleccionado está inactivo.");
        if (!Double.isFinite(monto) || monto <= 0) {
            throw new ValidationException("El saldo inicial debe ser mayor a cero.");
        }
        if (fechaReferencia == null) {
            throw new ValidationException("Indicá la fecha de referencia de la deuda anterior.");
        }
        if (fechaReferencia.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de referencia no puede estar en el futuro.");
        }
        return repository.registrarSaldoInicial(
                cliente.id(),
                usuario.id(),
                MoneyMath.guaranies(monto),
                fechaReferencia,
                normalizarOpcional(referencia, 120),
                normalizarOpcional(observacion, 300)
        );
    }

    public List<ImportacionClienteSaldoValidacion> validar(
            Usuario usuario,
            List<ImportacionClienteSaldoFila> filas
    ) {
        exigirPermiso(usuario);
        if (filas == null || filas.isEmpty()) {
            throw new ValidationException("El archivo no contiene clientes para importar.");
        }
        if (filas.size() > 5_000) {
            throw new ValidationException("La migración admite hasta 5.000 clientes por archivo.");
        }

        List<ImportacionClienteSaldoValidacion> preliminar = filas.stream()
                .map(this::validarFila)
                .toList();

        Map<String, Integer> documentos = new HashMap<>();
        Map<String, Integer> identidadesSinDocumento = new HashMap<>();
        for (var item : preliminar) {
            if (item.entrada() == null) continue;
            if (item.entrada().documento() != null) {
                documentos.merge(item.entrada().documento().toLowerCase(Locale.ROOT), 1, Integer::sum);
            } else {
                identidadesSinDocumento.merge(claveNombreTelefono(
                        item.entrada().nombre(), item.entrada().telefono()), 1, Integer::sum);
            }
        }

        List<ImportacionClienteSaldoValidacion> result = new ArrayList<>();
        for (var item : preliminar) {
            var entrada = item.entrada();
            List<String> errores = new ArrayList<>(item.errores());
            if (entrada != null) {
                if (entrada.documento() != null
                        && documentos.getOrDefault(entrada.documento().toLowerCase(Locale.ROOT), 0) > 1) {
                    errores.add("Documento/RUC repetido dentro del archivo");
                }
                if (entrada.documento() == null
                        && identidadesSinDocumento.getOrDefault(
                        claveNombreTelefono(entrada.nombre(), entrada.telefono()), 0) > 1) {
                    errores.add("Cliente sin documento repetido dentro del archivo");
                }

                if (errores.isEmpty()) {
                    MigracionClienteCoincidencia coincidencia = repository.buscarCoincidencia(
                            entrada.nombre(), entrada.documento(), entrada.telefono());
                    if (coincidencia.tieneSaldoInicial()) {
                        errores.add("El cliente ya tiene un saldo inicial migrado");
                    } else if (coincidencia.existeCliente()) {
                        entrada = new ImportacionClienteSaldoEntrada(
                                entrada.fila(),
                                entrada.nombre(),
                                entrada.documento(),
                                entrada.telefono(),
                                entrada.direccion(),
                                entrada.saldoInicial(),
                                entrada.fechaReferencia(),
                                entrada.referencia(),
                                entrada.observacion(),
                                coincidencia.clienteId()
                        );
                    }
                }
            }
            result.add(new ImportacionClienteSaldoValidacion(
                    item.fila(), item.cliente(), entrada, List.copyOf(errores)
            ));
        }
        return List.copyOf(result);
    }

    public ImportacionClienteSaldoResultado importar(
            Usuario usuario,
            List<ImportacionClienteSaldoValidacion> validaciones,
            String referenciaLote
    ) {
        exigirPermiso(usuario);
        if (validaciones == null) {
            throw new ValidationException("Primero revisá el archivo a migrar.");
        }
        List<ImportacionClienteSaldoEntrada> validas = validaciones.stream()
                .filter(ImportacionClienteSaldoValidacion::valida)
                .map(ImportacionClienteSaldoValidacion::entrada)
                .filter(Objects::nonNull)
                .toList();
        if (validas.isEmpty()) {
            throw new ValidationException("No hay filas válidas para migrar.");
        }
        return repository.importarSaldosIniciales(
                validas,
                usuario.id(),
                normalizarOpcional(referenciaLote, 120)
        );
    }

    private ImportacionClienteSaldoValidacion validarFila(ImportacionClienteSaldoFila fila) {
        List<String> errores = new ArrayList<>();

        String nombre = normalizar(fila.nombre());
        if (nombre == null) errores.add("Falta el nombre del cliente");
        else if (nombre.length() > 120) errores.add("El nombre supera 120 caracteres");

        String documento = normalizarOpcional(fila.documento(), 40);
        String telefono = normalizarOpcional(fila.telefono(), 40);
        String direccion = normalizarOpcional(fila.direccion(), 180);
        String referencia = normalizarOpcional(fila.referencia(), 120);
        String observacion = normalizarOpcional(fila.observacion(), 300);

        Double saldo = parseMonto(fila.saldoInicial(), errores);
        LocalDate fecha = parseFecha(fila.fechaReferencia(), errores);

        ImportacionClienteSaldoEntrada entrada = null;
        if (nombre != null && saldo != null && fecha != null) {
            entrada = new ImportacionClienteSaldoEntrada(
                    fila.fila(),
                    nombre,
                    documento,
                    telefono,
                    direccion,
                    MoneyMath.guaranies(saldo),
                    fecha,
                    referencia,
                    observacion,
                    null
            );
        }

        return new ImportacionClienteSaldoValidacion(
                fila.fila(),
                nombre == null ? "(sin nombre)" : nombre,
                entrada,
                List.copyOf(errores)
        );
    }

    private Double parseMonto(String value, List<String> errores) {
        String normalized = normalizar(value);
        if (normalized == null) {
            errores.add("Falta el saldo inicial");
            return null;
        }
        normalized = normalized
                .replace("Gs.", "")
                .replace("Gs", "")
                .replace("₲", "")
                .replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            double amount = Double.parseDouble(normalized);
            if (!Double.isFinite(amount) || amount <= EPSILON) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException e) {
            errores.add("Saldo inicial inválido");
            return null;
        }
    }

    private LocalDate parseFecha(String value, List<String> errores) {
        String normalized = normalizar(value);
        if (normalized == null) return LocalDate.now();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                LocalDate parsed = LocalDate.parse(normalized, formatter);
                if (parsed.isAfter(LocalDate.now())) {
                    errores.add("La fecha de referencia no puede estar en el futuro");
                    return null;
                }
                return parsed;
            } catch (DateTimeParseException ignored) {
            }
        }
        errores.add("Fecha inválida: usá dd/mm/aaaa");
        return null;
    }

    private String normalizar(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizarOpcional(String value, int max) {
        String normalized = normalizar(value);
        if (normalized == null) return null;
        if (normalized.length() > max) {
            throw new ValidationException("Uno de los campos supera el largo permitido.");
        }
        return normalized;
    }

    private String claveNombreTelefono(String nombre, String telefono) {
        return (nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT))
                + "|"
                + (telefono == null ? "" : telefono.trim().toLowerCase(Locale.ROOT));
    }

    private void exigirPermiso(Usuario usuario) {
        Objects.requireNonNull(usuario);
        autorizacionService.exigir(usuario, Permiso.FIADO_GESTIONAR);
    }
}
