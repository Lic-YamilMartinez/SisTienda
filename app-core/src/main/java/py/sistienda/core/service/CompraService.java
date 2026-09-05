package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CompraDetalle;
import py.sistienda.core.model.CompraResumen;
import py.sistienda.core.model.LineaCompra;
import py.sistienda.core.model.Proveedor;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.CompraRepository;

import java.util.List;
import java.util.Objects;

public final class CompraService {
    private final CompraRepository repository;

    public CompraService(CompraRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public long registrar(Usuario usuario, Proveedor proveedor, String nroDocumento, List<LineaCompra> lineas, String observacion) {
        Objects.requireNonNull(usuario);
        Objects.requireNonNull(proveedor);
        if (lineas == null || lineas.isEmpty()) {
            throw new ValidationException("Agregá al menos un producto a la compra.");
        }
        for (LineaCompra linea : lineas) {
            if (linea == null || linea.producto() == null) {
                throw new ValidationException("Hay una línea de compra inválida.");
            }
            if (linea.cantidad() <= 0) {
                throw new ValidationException("La cantidad de cada producto debe ser mayor a cero.");
            }
            if (linea.producto().unidadMedida() == UnidadMedida.UN
                    && Math.abs(linea.cantidad() - Math.rint(linea.cantidad())) > 0.000001) {
                throw new ValidationException(linea.producto().nombre() + " se compra por unidad y no acepta decimales.");
            }
            if (linea.costoUnitario() < 0) {
                throw new ValidationException("El costo no puede ser negativo.");
            }
        }
        return repository.registrar(
                usuario,
                proveedor,
                nullable(nroDocumento),
                List.copyOf(lineas),
                nullable(observacion)
        );
    }

    public List<CompraResumen> listarRecientes() {
        return repository.listarRecientes(100);
    }

    public CompraDetalle detalle(long compraId) {
        return repository.detalle(compraId);
    }

    private String nullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
