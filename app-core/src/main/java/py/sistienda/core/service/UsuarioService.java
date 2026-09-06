package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.RolUsuario;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.UsuarioRepository;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.PasswordHasher;
import py.sistienda.core.security.Permiso;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;
    private final AutorizacionService autorizacionService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher,
                          AutorizacionService autorizacionService) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.autorizacionService = Objects.requireNonNull(autorizacionService);
    }

    public List<Usuario> listar(Usuario actor) {
        autorizacionService.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        return usuarioRepository.findAll();
    }

    public Usuario crear(Usuario actor, String username, char[] password, RolUsuario rol) {
        autorizacionService.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        String normalized = validarUsername(username);
        validarPassword(password);
        if (rol == null) {
            throw new ValidationException("Seleccioná un rol para el usuario.");
        }
        return usuarioRepository.create(normalized, passwordHasher.hash(password), rol);
    }

    public Usuario cambiarRol(Usuario actor, long usuarioId, RolUsuario nuevoRol) {
        autorizacionService.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        if (nuevoRol == null) {
            throw new ValidationException("Seleccioná un rol válido.");
        }
        Usuario target = obtener(usuarioId);
        if (target.id() == actor.id() && nuevoRol != RolUsuario.DUENIO) {
            throw new ValidationException("No podés quitarte a vos mismo el rol de Dueño.");
        }
        if (target.rolUsuario() == RolUsuario.DUENIO && nuevoRol != RolUsuario.DUENIO
                && target.activo() && usuarioRepository.countActiveByRole(RolUsuario.DUENIO) <= 1) {
            throw new ValidationException("La tienda debe conservar al menos un Dueño activo.");
        }
        return usuarioRepository.updateRole(usuarioId, nuevoRol);
    }

    public Usuario cambiarEstado(Usuario actor, long usuarioId, boolean activo) {
        autorizacionService.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        Usuario target = obtener(usuarioId);
        if (!activo && target.id() == actor.id()) {
            throw new ValidationException("No podés desactivar tu propia sesión.");
        }
        if (!activo && target.activo() && target.rolUsuario() == RolUsuario.DUENIO
                && usuarioRepository.countActiveByRole(RolUsuario.DUENIO) <= 1) {
            throw new ValidationException("La tienda debe conservar al menos un Dueño activo.");
        }
        return usuarioRepository.setActive(usuarioId, activo);
    }

    public void restablecerPassword(Usuario actor, long usuarioId, char[] nuevaPassword) {
        autorizacionService.exigir(actor, Permiso.USUARIOS_GESTIONAR);
        obtener(usuarioId);
        validarPassword(nuevaPassword);
        usuarioRepository.updatePassword(usuarioId, passwordHasher.hash(nuevaPassword));
    }

    public void cambiarMiPassword(Usuario actor, char[] passwordActual, char[] nuevaPassword) {
        Objects.requireNonNull(actor);
        if (passwordActual == null || passwordActual.length == 0) {
            throw new ValidationException("Ingresá tu contraseña actual.");
        }
        validarPassword(nuevaPassword);
        var credential = usuarioRepository.findActiveByUsername(actor.username())
                .orElseThrow(() -> new ValidationException("Tu usuario ya no está activo."));
        if (!passwordHasher.verify(passwordActual, credential.passwordHash())) {
            throw new ValidationException("La contraseña actual no es correcta.");
        }
        usuarioRepository.updatePassword(actor.id(), passwordHasher.hash(nuevaPassword));
    }

    private Usuario obtener(long id) {
        if (id <= 0) {
            throw new ValidationException("Usuario inválido.");
        }
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ValidationException("El usuario ya no existe."));
    }

    private String validarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Ingresá el nombre de usuario.");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 50) {
            throw new ValidationException("El usuario debe tener entre 3 y 50 caracteres.");
        }
        if (!normalized.matches("[a-z0-9._-]+")) {
            throw new ValidationException("Usá sólo letras, números, punto, guion o guion bajo.");
        }
        return normalized;
    }

    private void validarPassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new ValidationException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (password.length > 128) {
            throw new ValidationException("La contraseña es demasiado larga.");
        }
    }
}
