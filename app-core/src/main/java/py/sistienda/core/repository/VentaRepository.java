package py.sistienda.core.repository;

import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.PagoVenta;
import py.sistienda.core.model.VentaResultado;

import java.util.List;

public interface VentaRepository {

    VentaResultado register(
            long cajaSesionId,
            long usuarioId,
            MetodoPago metodoPago,
            double recibido,
            double vuelto,
            List<LineaVenta> lineas
    );

    default VentaResultado register(
            long cajaSesionId,
            long usuarioId,
            MetodoPago metodoPago,
            double recibido,
            double vuelto,
            Long clienteId,
            List<LineaVenta> lineas
    ) {
        if (clienteId != null) {
            throw new UnsupportedOperationException("Este repositorio todavía no soporta ventas asociadas a clientes.");
        }
        return register(cajaSesionId, usuarioId, metodoPago, recibido, vuelto, lineas);
    }

    default VentaResultado register(
            long cajaSesionId,
            long usuarioId,
            List<PagoVenta> pagos,
            double recibido,
            double vuelto,
            Long clienteId,
            List<LineaVenta> lineas
    ) {
        if (pagos == null || pagos.size() != 1) {
            throw new UnsupportedOperationException("Este repositorio todavía no soporta pagos mixtos.");
        }
        PagoVenta pago = pagos.getFirst();
        return register(cajaSesionId, usuarioId, pago.metodoPago(), recibido, vuelto, clienteId, lineas);
    }
}
