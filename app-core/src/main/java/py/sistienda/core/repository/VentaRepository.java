package py.sistienda.core.repository;

import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
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
}
