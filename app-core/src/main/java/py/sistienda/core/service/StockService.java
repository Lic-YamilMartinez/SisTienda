package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.TipoMovimientoStock;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.repository.MovimientoStockRepository;

import java.util.Objects;

public final class StockService {

    private final MovimientoStockRepository movimientoStockRepository;

    public StockService(MovimientoStockRepository movimientoStockRepository) {
        this.movimientoStockRepository = Objects.requireNonNull(movimientoStockRepository);
    }

    public void registrar(Producto producto, TipoMovimientoStock tipo, String motivo, double cantidad,
                          String referencia, String observacion) {
        Objects.requireNonNull(producto);
        if (producto.id() <= 0) {
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
        if (producto.unidadMedida() == UnidadMedida.UN && cantidad != Math.rint(cantidad)) {
            throw new ValidationException("Los productos por unidad deben moverse en cantidades enteras.");
        }
        if (tipo == TipoMovimientoStock.SALIDA && cantidad > producto.stockActual()) {
            throw new ValidationException("No hay stock suficiente para realizar esa salida.");
        }

        movimientoStockRepository.register(
                producto.id(),
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
