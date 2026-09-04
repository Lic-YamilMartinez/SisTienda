package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.TipoMovimientoStock;
import py.sistienda.core.repository.MovimientoStockRepository;

import java.util.Objects;

public final class StockService {

    private final MovimientoStockRepository movimientoStockRepository;

    public StockService(MovimientoStockRepository movimientoStockRepository) {
        this.movimientoStockRepository = Objects.requireNonNull(movimientoStockRepository);
    }

    public void registrar(long productoId, TipoMovimientoStock tipo, String motivo, double cantidad,
                          String referencia, String observacion) {
        if (productoId <= 0) {
            throw new ValidationException("Producto inválido.");
        }
        if (tipo == null) {
            throw new ValidationException("Seleccioná el tipo de movimiento.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ValidationException("Indicá el motivo del movimiento.");
        }
        if (!Double.isFinite(cantidad) || cantidad <= 0) {
            throw new ValidationException("La cantidad debe ser mayor a cero.");
        }

        movimientoStockRepository.register(
                productoId,
                tipo,
                motivo.trim(),
                cantidad,
                limpiarOpcional(referencia),
                limpiarOpcional(observacion)
        );
    }

    private String limpiarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
