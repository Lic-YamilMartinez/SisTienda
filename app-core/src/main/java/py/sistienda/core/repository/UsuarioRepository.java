package py.sistienda.core.repository;

import py.sistienda.core.model.RolUsuario;
import py.sistienda.core.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository {

    boolean existsActiveUser();

    Optional<UsuarioCredential> findActiveByUsername(String username);

    Usuario createOwner(String username, String passwordHash);

    default List<Usuario> findAll() {
        throw new UnsupportedOperationException("Listado de usuarios no implementado.");
    }

    default Optional<Usuario> findById(long id) {
        throw new UnsupportedOperationException("Búsqueda de usuario no implementada.");
    }

    default Usuario create(String username, String passwordHash, RolUsuario rol) {
        throw new UnsupportedOperationException("Creación de usuarios no implementada.");
    }

    default Usuario updateRole(long id, RolUsuario rol) {
        throw new UnsupportedOperationException("Actualización de rol no implementada.");
    }

    default Usuario setActive(long id, boolean activo) {
        throw new UnsupportedOperationException("Actualización de estado no implementada.");
    }

    default void updatePassword(long id, String passwordHash) {
        throw new UnsupportedOperationException("Actualización de contraseña no implementada.");
    }

    default long countActiveByRole(RolUsuario rol) {
        throw new UnsupportedOperationException("Conteo por rol no implementado.");
    }

    record UsuarioCredential(
            long id,
            String username,
            String passwordHash,
            String rol,
            boolean activo
    ) {
        public Usuario toUsuario() {
            return new Usuario(id, username, rol, activo);
        }
    }
}
