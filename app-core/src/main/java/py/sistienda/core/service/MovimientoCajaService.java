package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.ControlEfectivoCaja;
import py.sistienda.core.model.MovimientoCaja;
import py.sistienda.core.model.TipoMovimientoCaja;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.MovimientoCajaRepository;

import java.util.List;
import java.util.Objects;

public final class MovimientoCajaService {
    private final MovimientoCajaRepository repository;

    public MovimientoCajaService(MovimientoCajaRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public MovimientoCaja registrar(CajaSesion caja, Usuario usuario, TipoMovimientoCaja tipo,
                                     String categoria, String concepto, double monto, String referencia) {
        Objects.requireNonNull(caja);
        Objects.requireNonNull(usuario);
        Objects.requireNonNull(tipo);
        if (!caja.abierta()) {
            throw new ValidationException("La caja debe estar abierta para registrar movimientos.");
        }
        if (!Double.isFinite(monto) || monto <= 0) {
            throw new ValidationException("El monto debe ser mayor a cero.");
        }
        String categoriaNormalizada = normalizarRequerido(categoria, "Seleccioná una categoría.", 60);
        String conceptoNormalizado = normalizarRequerido(concepto, "Ingresá un concepto.", 120);
        String referenciaNormalizada = normalizarOpcional(referencia, 120);
        return repository.create(caja.id(), usuario.id(), tipo, categoriaNormalizada,
                conceptoNormalizado, monto, referenciaNormalizada);
    }

    public List<MovimientoCaja> listar(CajaSesion caja) {
        Objects.requireNonNull(caja);
        return repository.findByCaja(caja.id());
    }

    public ControlEfectivoCaja control(CajaSesion caja, double ventasEfectivo) {
        Objects.requireNonNull(caja);
        if (!Double.isFinite(ventasEfectivo) || ventasEfectivo < 0) {
            throw new ValidationException("Las ventas en efectivo no son válidas.");
        }
        var resumen = repository.summary(caja.id());
        double esperado = caja.montoApertura() + ventasEfectivo + resumen.ingresos() - resumen.egresos();
        return new ControlEfectivoCaja(caja.montoApertura(), ventasEfectivo,
                resumen.ingresos(), resumen.egresos(), esperado);
    }

    private String normalizarRequerido(String value, String message, int max) {
        String normalized = normalizarOpcional(value, max);
        if (normalized == null) throw new ValidationException(message);
        return normalized;
    }

    private String normalizarOpcional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > max) {
            throw new ValidationException("El texto es demasiado largo.");
        }
        return normalized;
    }
}
