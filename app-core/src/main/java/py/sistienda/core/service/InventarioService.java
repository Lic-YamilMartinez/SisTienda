package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.InventarioConteoDetalle;
import py.sistienda.core.model.InventarioConteoItem;
import py.sistienda.core.model.InventarioConteoResultado;
import py.sistienda.core.model.InventarioConteoResumen;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.InventarioRepository;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class InventarioService {
    private static final double EPSILON = 0.000001d;
    private static final int MAX_ITEMS = 5_000;

    private final InventarioRepository repository;
    private final AutorizacionService autorizacionService;

    public InventarioService(InventarioRepository repository, AutorizacionService autorizacionService) {
        this.repository = Objects.requireNonNull(repository);
        this.autorizacionService = Objects.requireNonNull(autorizacionService);
    }

    public InventarioConteoResultado registrar(
            Usuario usuario,
            String motivo,
            String observacion,
            List<InventarioConteoItem> items
    ) {
        exigirPermiso(usuario);
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Contá al menos un producto antes de confirmar el inventario.");
        }
        if (items.size() > MAX_ITEMS) {
            throw new ValidationException("El conteo supera el máximo de " + MAX_ITEMS + " productos por operación.");
        }

        String motivoNormalizado = normalizarRequerido(motivo, 120,
                "Indicá el motivo del conteo, por ejemplo: Conteo semanal.");
        String observacionNormalizada = normalizarOpcional(observacion, 300);

        Set<Long> ids = new HashSet<>();
        for (InventarioConteoItem item : items) {
            validarItem(item);
            if (!ids.add(item.productoId())) {
                throw new ValidationException("Hay productos repetidos en el conteo.");
            }
        }

        return repository.registrar(
                usuario.id(),
                motivoNormalizado,
                observacionNormalizada,
                List.copyOf(items)
        );
    }

    public List<InventarioConteoResumen> recientes(Usuario usuario, int limite) {
        exigirPermiso(usuario);
        return repository.recientes(Math.max(1, Math.min(limite, 200)));
    }

    public List<InventarioConteoDetalle> detalle(Usuario usuario, long inventarioId) {
        exigirPermiso(usuario);
        if (inventarioId <= 0) throw new ValidationException("Inventario inválido.");
        return repository.detalle(inventarioId);
    }

    private void validarItem(InventarioConteoItem item) {
        if (item == null || item.productoId() <= 0) {
            throw new ValidationException("Hay un producto inválido en el conteo.");
        }
        if (item.unidadMedida() == null) {
            throw new ValidationException("La unidad de medida del producto no es válida.");
        }
        if (!Double.isFinite(item.stockSistema()) || item.stockSistema() < 0) {
            throw new ValidationException("El stock del sistema no es válido para “" + item.productoNombre() + "”.");
        }
        if (!Double.isFinite(item.stockFisico()) || item.stockFisico() < 0) {
            throw new ValidationException("El conteo físico debe ser cero o mayor para “" + item.productoNombre() + "”.");
        }
        if (item.unidadMedida() == UnidadMedida.UN
                && Math.abs(item.stockFisico() - Math.rint(item.stockFisico())) > EPSILON) {
            throw new ValidationException("“" + item.productoNombre() + "” se controla por unidad y no acepta decimales.");
        }
    }

    private void exigirPermiso(Usuario usuario) {
        Objects.requireNonNull(usuario);
        autorizacionService.exigir(usuario, Permiso.INVENTARIO_GESTIONAR);
    }

    private String normalizarRequerido(String value, int max, String mensaje) {
        String normalized = normalizarOpcional(value, max);
        if (normalized == null) throw new ValidationException(mensaje);
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
