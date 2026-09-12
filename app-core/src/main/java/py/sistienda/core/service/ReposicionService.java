package py.sistienda.core.service;

import py.sistienda.core.model.Producto;
import py.sistienda.core.model.ReposicionItem;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ReposicionService {

    private final ProductoService productoService;

    public ReposicionService(ProductoService productoService) {
        this.productoService = Objects.requireNonNull(productoService);
    }

    public List<ReposicionItem> listarPendientes() {
        return productoService.listarActivos().stream()
                .filter(Producto::necesitaReposicion)
                .map(producto -> new ReposicionItem(
                        producto,
                        producto.cantidadSugeridaReposicion(),
                        producto.cantidadSugeridaReposicion() * producto.costo()
                ))
                .sorted(Comparator
                        .comparing(ReposicionItem::sinStock).reversed()
                        .thenComparingDouble(item -> urgencia(item.producto()))
                        .thenComparing(item -> item.producto().nombre(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public long contarConfigurados() {
        return productoService.listarActivos().stream().filter(Producto::reposicionConfigurada).count();
    }

    public Producto actualizarNiveles(Producto producto, double minimo, double ideal) {
        return productoService.actualizarReposicion(producto, minimo, ideal);
    }

    private double urgencia(Producto producto) {
        if (producto.stockMinimo() <= 0.000001d) return producto.stockActual();
        return producto.stockActual() / producto.stockMinimo();
    }
}
