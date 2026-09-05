package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Proveedor;
import py.sistienda.core.repository.ProveedorRepository;

import java.util.List;
import java.util.Objects;

public final class ProveedorService {
    private final ProveedorRepository repository;

    public ProveedorService(ProveedorRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<Proveedor> listarActivos() {
        return repository.findAllActive();
    }

    public Proveedor crear(String nombre, String ruc, String telefono, String email, String direccion) {
        return repository.create(validar(new Proveedor(0, nombre, ruc, telefono, email, direccion, true)));
    }

    public Proveedor actualizar(Proveedor actual, String nombre, String ruc, String telefono, String email, String direccion) {
        Objects.requireNonNull(actual);
        return repository.update(validar(new Proveedor(actual.id(), nombre, ruc, telefono, email, direccion, actual.activo())));
    }

    public void desactivar(Proveedor proveedor) {
        Objects.requireNonNull(proveedor);
        repository.deactivate(proveedor.id());
    }

    private Proveedor validar(Proveedor proveedor) {
        String nombre = clean(proveedor.nombre());
        if (nombre.isBlank()) {
            throw new ValidationException("Ingresá el nombre del proveedor.");
        }
        if (nombre.length() > 120) {
            throw new ValidationException("El nombre del proveedor es demasiado largo.");
        }
        String email = nullable(proveedor.email());
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Revisá el correo del proveedor.");
        }
        return new Proveedor(
                proveedor.id(),
                nombre,
                nullable(proveedor.ruc()),
                nullable(proveedor.telefono()),
                email,
                nullable(proveedor.direccion()),
                proveedor.activo()
        );
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullable(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }
}
