package py.sistienda.core.model;

import py.sistienda.core.exception.ValidationException;

import java.util.Locale;

public enum RolUsuario {
    DUENIO("Dueño"),
    ADMINISTRADOR("Administrador"),
    CAJERO("Cajero"),
    VENDEDOR("Vendedor");

    private final String descripcion;

    RolUsuario(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }

    public static RolUsuario desde(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("El usuario no tiene un rol válido.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Rol de usuario no reconocido: " + value);
        }
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
