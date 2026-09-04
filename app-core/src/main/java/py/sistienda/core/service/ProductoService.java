package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.repository.ProductoRepository;

import java.util.List;
import java.util.Objects;

public final class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = Objects.requireNonNull(productoRepository);
    }

    public List<Producto> listarActivos() {
        return productoRepository.findAllActive();
    }

    public Producto crear(String nombre, Long categoriaId, String categoriaNombre,
                           UnidadMedida unidadMedida, double precioVenta, double costo) {
        validar(nombre, unidadMedida, precioVenta, costo);
        Producto nuevo = new Producto(0L, normalizarNombre(nombre), categoriaId, categoriaNombre,
                unidadMedida, precioVenta, costo, 0d, true);
        return productoRepository.create(nuevo);
    }

    public Producto actualizar(Producto actual, String nombre, Long categoriaId, String categoriaNombre,
                               UnidadMedida unidadMedida, double precioVenta, double costo) {
        Objects.requireNonNull(actual);
        validar(nombre, unidadMedida, precioVenta, costo);
        Producto actualizado = new Producto(actual.id(), normalizarNombre(nombre), categoriaId,
                categoriaNombre, unidadMedida, precioVenta, costo, actual.stockActual(), actual.activo());
        return productoRepository.update(actualizado);
    }

    public void desactivar(long productoId) {
        if (productoId <= 0) {
            throw new ValidationException("Producto inválido.");
        }
        productoRepository.deactivate(productoId);
    }

    private void validar(String nombre, UnidadMedida unidadMedida, double precioVenta, double costo) {
        if (nombre == null || nombre.isBlank()) {
            throw new ValidationException("Ingresá el nombre del producto.");
        }
        if (nombre.trim().length() > 120) {
            throw new ValidationException("El nombre del producto es demasiado largo.");
        }
        if (unidadMedida == null) {
            throw new ValidationException("Seleccioná cómo se vende el producto.");
        }
        if (!Double.isFinite(precioVenta) || precioVenta < 0) {
            throw new ValidationException("El precio de venta debe ser igual o mayor a cero.");
        }
        if (!Double.isFinite(costo) || costo < 0) {
            throw new ValidationException("El costo debe ser igual o mayor a cero.");
        }
    }

    private String normalizarNombre(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }
}
