package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Empresa;
import py.sistienda.core.repository.EmpresaRepository;

import java.util.Objects;

public final class EmpresaService {

    private final EmpresaRepository repository;

    public EmpresaService(EmpresaRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Empresa obtener() {
        return repository.get();
    }

    public Empresa guardar(String nombre, String ruc, String direccion, String telefono, String mensajeTicket) {
        String nombreNormalizado = normalizarRequerido(nombre, "El nombre de la tienda", 100);
        return repository.save(new Empresa(
                1L,
                nombreNormalizado,
                normalizarOpcional(ruc, 40),
                normalizarOpcional(direccion, 180),
                normalizarOpcional(telefono, 50),
                normalizarOpcional(mensajeTicket, 180)
        ));
    }

    private String normalizarRequerido(String value, String campo, int max) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(campo + " es obligatorio.");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > max) {
            throw new ValidationException(campo + " es demasiado largo.");
        }
        return normalized;
    }

    private String normalizarOpcional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > max) {
            throw new ValidationException("Uno de los datos de la tienda supera el largo permitido.");
        }
        return normalized;
    }
}
