package py.sistienda.core.repository;

import py.sistienda.core.model.Usuario;

import java.util.Optional;

public interface UsuarioRepository {

    boolean existsActiveUser();

    Optional<UsuarioCredential> findActiveByUsername(String username);

    Usuario createOwner(String username, String passwordHash);

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
