package py.sistienda.core.repository;

import py.sistienda.core.model.DevolucionLineaSolicitud;
import py.sistienda.core.model.DevolucionResultado;
import py.sistienda.core.model.VentaPostventa;

import java.util.List;
import java.util.Optional;

public interface PostventaRepository {

    Optional<VentaPostventa> findVenta(long ventaId);

    void anular(long ventaId, long usuarioId, String motivo);

    DevolucionResultado devolver(
            long ventaId,
            long cajaSesionId,
            long usuarioId,
            String motivo,
            List<DevolucionLineaSolicitud> lineas
    );
}
