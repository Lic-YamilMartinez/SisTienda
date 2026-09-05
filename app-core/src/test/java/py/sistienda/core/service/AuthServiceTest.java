package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.UsuarioRepository;
import py.sistienda.core.security.PasswordHasher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    @Test
    void crearDuenoInicial_hashesPasswordYPermiteLogin() {
        FakeUsuarioRepository repository = new FakeUsuarioRepository();
        AuthService service = new AuthService(repository, new PasswordHasher());

        assertTrue(service.requiereConfiguracionInicial());

        Usuario creado = service.crearDuenoInicial(" Admin ", "segura123".toCharArray());

        assertEquals("admin", creado.username());
        assertFalse(service.requiereConfiguracionInicial());

        Usuario login = service.iniciarSesion("ADMIN", "segura123".toCharArray());
        assertEquals(creado.id(), login.id());
    }

    @Test
    void iniciarSesion_rechazaContrasenaIncorrecta() {
        FakeUsuarioRepository repository = new FakeUsuarioRepository();
        AuthService service = new AuthService(repository, new PasswordHasher());
        service.crearDuenoInicial("admin", "segura123".toCharArray());

        assertThrows(ValidationException.class,
                () -> service.iniciarSesion("admin", "otra-clave".toCharArray()));
    }

    @Test
    void crearDuenoInicial_rechazaPasswordCorta() {
        AuthService service = new AuthService(new FakeUsuarioRepository(), new PasswordHasher());

        assertThrows(ValidationException.class,
                () -> service.crearDuenoInicial("admin", "123".toCharArray()));
    }

    private static final class FakeUsuarioRepository implements UsuarioRepository {
        private UsuarioCredential credential;

        @Override
        public boolean existsActiveUser() {
            return credential != null && credential.activo();
        }

        @Override
        public Optional<UsuarioCredential> findActiveByUsername(String username) {
            if (credential != null && credential.username().equals(username) && credential.activo()) {
                return Optional.of(credential);
            }
            return Optional.empty();
        }

        @Override
        public Usuario createOwner(String username, String passwordHash) {
            credential = new UsuarioCredential(1L, username, passwordHash, "DUENIO", true);
            return credential.toUsuario();
        }
    }
}
