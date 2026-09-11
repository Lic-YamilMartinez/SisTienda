package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.DevolucionLineaSolicitud;
import py.sistienda.core.model.DevolucionResultado;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaPostventa;
import py.sistienda.core.model.VentaPostventaLinea;
import py.sistienda.core.repository.PostventaRepository;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PostventaService {
    private static final double EPSILON = 0.000001d;

    private final PostventaRepository repository;
    private final AutorizacionService autorizacionService;

    public PostventaService(PostventaRepository repository, AutorizacionService autorizacionService) {
        this.repository = Objects.requireNonNull(repository);
        this.autorizacionService = Objects.requireNonNull(autorizacionService);
    }

    public VentaPostventa obtenerVenta(Usuario usuario, long ventaId) {
        exigirPermiso(usuario);
        validarVentaId(ventaId);
        return repository.findVenta(ventaId)
                .orElseThrow(() -> new ValidationException("No se encontró la venta seleccionada."));
    }

    public void anular(Usuario usuario, long ventaId, String motivo) {
        exigirPermiso(usuario);
        validarVentaId(ventaId);
        String motivoNormalizado = normalizarMotivo(motivo);
        VentaPostventa venta = repository.findVenta(ventaId)
                .orElseThrow(() -> new ValidationException("No se encontró la venta seleccionada."));
        if (venta.anulada()) {
            throw new ValidationException("La venta ya está anulada.");
        }
        if (venta.tieneDevoluciones()) {
            throw new ValidationException("La venta ya tiene devoluciones. Devolvé los artículos restantes en lugar de anularla.");
        }
        repository.anular(ventaId, usuario.id(), motivoNormalizado);
    }

    public DevolucionResultado devolver(
            Usuario usuario,
            CajaSesion caja,
            long ventaId,
            String motivo,
            List<DevolucionLineaSolicitud> solicitudes
    ) {
        exigirPermiso(usuario);
        Objects.requireNonNull(caja);
        validarVentaId(ventaId);
        String motivoNormalizado = normalizarMotivo(motivo);

        if (!caja.abierta()) {
            throw new ValidationException("Abrí una caja para registrar la devolución.");
        }
        if (caja.usuarioId() != usuario.id()) {
            throw new ValidationException("La caja abierta pertenece a otro usuario.");
        }
        if (solicitudes == null || solicitudes.isEmpty()) {
            throw new ValidationException("Seleccioná al menos un producto para devolver.");
        }

        VentaPostventa venta = repository.findVenta(ventaId)
                .orElseThrow(() -> new ValidationException("No se encontró la venta seleccionada."));
        if (venta.anulada()) {
            throw new ValidationException("No se puede devolver una venta anulada.");
        }

        Map<Long, VentaPostventaLinea> porDetalle = new HashMap<>();
        for (VentaPostventaLinea linea : venta.lineas()) {
            porDetalle.put(linea.ventaDetalleId(), linea);
        }

        Set<Long> repetidos = new HashSet<>();
        for (DevolucionLineaSolicitud solicitud : solicitudes) {
            if (solicitud == null || solicitud.ventaDetalleId() <= 0) {
                throw new ValidationException("Hay una línea de devolución inválida.");
            }
            if (!repetidos.add(solicitud.ventaDetalleId())) {
                throw new ValidationException("Un producto está repetido en la devolución.");
            }
            VentaPostventaLinea linea = porDetalle.get(solicitud.ventaDetalleId());
            if (linea == null) {
                throw new ValidationException("Uno de los productos no pertenece a la venta.");
            }
            double cantidad = solicitud.cantidad();
            if (!Double.isFinite(cantidad) || cantidad <= 0) {
                throw new ValidationException("La cantidad a devolver debe ser mayor a cero.");
            }
            if (linea.unidadMedida() == UnidadMedida.UN
                    && Math.abs(cantidad - Math.rint(cantidad)) > EPSILON) {
                throw new ValidationException("“" + linea.producto() + "” se devuelve por unidad y no acepta decimales.");
            }
            if (cantidad - linea.cantidadDisponible() > EPSILON) {
                throw new ValidationException("No podés devolver más cantidad de “" + linea.producto() + "” que la disponible.");
            }
        }

        return repository.devolver(
                ventaId,
                caja.id(),
                usuario.id(),
                motivoNormalizado,
                List.copyOf(solicitudes)
        );
    }

    private void exigirPermiso(Usuario usuario) {
        Objects.requireNonNull(usuario);
        autorizacionService.exigir(usuario, Permiso.POSTVENTA_GESTIONAR);
    }

    private void validarVentaId(long ventaId) {
        if (ventaId <= 0) {
            throw new ValidationException("La venta seleccionada no es válida.");
        }
    }

    private String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ValidationException("Indicá el motivo de la operación.");
        }
        String normalized = motivo.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 3) {
            throw new ValidationException("El motivo es demasiado corto.");
        }
        if (normalized.length() > 300) {
            throw new ValidationException("El motivo no puede superar 300 caracteres.");
        }
        return normalized;
    }
}
