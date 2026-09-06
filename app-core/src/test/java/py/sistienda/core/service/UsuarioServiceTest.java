package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.RolUsuario;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.UsuarioRepository;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.PasswordHasher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioServiceTest {

    @Test
    void duenoCreaUsuarioYNormalizaUsername() {
        FakeUsuarioRepository repository = new FakeUsuarioRepository();
        Usuario dueno = repository.seed("dueno", RolUsuario.DUENIO, true, "password1");
        UsuarioService service = service(repository);

        Usuario creado = service.crear(dueno, "  CAJA_01 ", "segura123".toCharArray(), RolUsuario.CAJERO);

        assertEquals("caja_01", creado.username());
        assertEquals(RolUsuario.CAJERO, creado.rolUsuario());
        assertTrue(creado.activo());
    }

    @Test
    void administradorNoPuedeGestionarUsuarios() {
        FakeUsuarioRepository repository = new FakeUsuarioRepository();
        Usuario admin = repository.seed("admin", RolUsuario.ADMINISTRADOR, true, "password1");
        UsuarioService service = service(repository);

        assertThrows(ValidationException.class, () -> service.listar(admin));
        assertThrows(ValidationException.class, () ->
                service.crear(admin, "cajero", "password1".toCharArray(), RolUsuario.CAJERO));
    }

    @Test
    void noPermiteDesactivarLaPropiaSesion() {
        FakeUsuarioRepository repository = new FakeUsuarioRepository();
        Usuario dueno = repository.seed("dueno", RolUsuario.DUENIO, true, "password1");
        UsuarioService service = service(repository);

        assertThrows(ValidationException.class, () -> service.cambiarEstado(dueno, dueno.id(), false));
        assertTrue(repository.findById(dueno.id()).orElseThrow().activo());
    }

    @Test
    void noPermiteQuitarElUltimoDuenoActivo() {
        FakeUsuarioRepository repository = new FakeUsuarioRepository();
        Usuario dueno = repository.seed("dueno", RolUsuario.DUENIO, true, "password1");
        Usuario otro = repository.seed("otro", RolUsuario.ADMINISTRADOR, true, "password1");
        UsuarioService service = service(repository);

        assertThrows(ValidationException.class,
                () -> service.cambiarRol(dueno, dueno.id(), RolUsuario.ADMINISTRADOR));
        assertThrows(ValidationException.class,
                () -> service.cambiarEstado(dueno, dueno.id(), false));
        assertEquals(RolUsuario.DUENIO, repository.findById(dueno.id()).orElseThrow().rolUsuario());
        assertTrue(repository.findById(otro.id()).isPresent());
    }

    @Test
    void duenoPuedeDesactivarOtroUsuario() {
        FakeUsuarioRepository repository = new FakeUsuarioRepository();
        Usuario dueno = repository.seed("dueno", RolUsuario.DUENIO, true, "password1");
        Usuario cajero = repository.seed("cajero", RolUsuario.CAJERO, true, "password1");
        UsuarioService service = service(repository);

        Usuario actualizado = service.cambiarEstado(dueno, cajero.id(), false);

        assertFalse(actualizado.activo());
    }

    @Test
    void usuarioPuedeCambiarSuPropiaPassword() {
        FakeUsuarioRepository repository = new FakeUsuarioRepository();
        Usuario cajero = repository.seed("cajero", RolUsuario.CAJERO, true, "password1");
        UsuarioService service = service(repository);

        service.cambiarMiPassword(cajero, "password1".toCharArray(), "nuevaClave9".toCharArray());

        PasswordHasher hasher = new PasswordHasher();
        String hash = repository.credentials.get(cajero.id());
        assertTrue(hasher.verify("nuevaClave9".toCharArray(), hash));
        assertFalse(hasher.verify("password1".toCharArray(), hash));
    }

    private UsuarioService service(FakeUsuarioRepository repository) {
        return new UsuarioService(repository, new PasswordHasher(), new AutorizacionService());
    }

    private static final class FakeUsuarioRepository implements UsuarioRepository {
        private final List<Usuario> usuarios = new ArrayList<>();
        private final Map<Long, String> credentials = new HashMap<>();
        private long nextId = 1;

        Usuario seed(String username, RolUsuario rol, boolean activo, String password) {
            Usuario usuario = new Usuario(nextId++, username, rol.name(), activo);
            usuarios.add(usuario);
            credentials.put(usuario.id(), new PasswordHasher().hash(password.toCharArray()));
            return usuario;
        }

        @Override
        public boolean existsActiveUser() {
            return usuarios.stream().anyMatch(Usuario::activo);
        }

        @Override
        public Optional<UsuarioCredential> findActiveByUsername(String username) {
            return usuarios.stream()
                    .filter(Usuario::activo)
                    .filter(user -> user.username().equals(username))
                    .findFirst()
                    .map(user -> new UsuarioCredential(user.id(), user.username(), credentials.get(user.id()),
                            user.rol(), user.activo()));
        }

        @Override
        public Usuario createOwner(String username, String passwordHash) {
            return create(username, passwordHash, RolUsuario.DUENIO);
        }

        @Override
        public List<Usuario> findAll() {
            return List.copyOf(usuarios);
        }

        @Override
        public Optional<Usuario> findById(long id) {
            return usuarios.stream().filter(user -> user.id() == id).findFirst();
        }

        @Override
        public Usuario create(String username, String passwordHash, RolUsuario rol) {
            Usuario usuario = new Usuario(nextId++, username, rol.name(), true);
            usuarios.add(usuario);
            credentials.put(usuario.id(), passwordHash);
            return usuario;
        }

        @Override
        public Usuario updateRole(long id, RolUsuario rol) {
            Usuario current = findById(id).orElseThrow();
            Usuario updated = new Usuario(current.id(), current.username(), rol.name(), current.activo());
            replace(updated);
            return updated;
        }

        @Override
        public Usuario setActive(long id, boolean activo) {
            Usuario current = findById(id).orElseThrow();
            Usuario updated = new Usuario(current.id(), current.username(), current.rol(), activo);
            replace(updated);
            return updated;
        }

        @Override
        public void updatePassword(long id, String passwordHash) {
            credentials.put(id, passwordHash);
        }

        @Override
        public long countActiveByRole(RolUsuario rol) {
            return usuarios.stream().filter(Usuario::activo).filter(user -> user.rolUsuario() == rol).count();
        }

        private void replace(Usuario updated) {
            for (int i = 0; i < usuarios.size(); i++) {
                if (usuarios.get(i).id() == updated.id()) {
                    usuarios.set(i, updated);
                    return;
                }
            }
        }
    }
}
