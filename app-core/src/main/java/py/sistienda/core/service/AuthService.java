package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.UsuarioRepository;
import py.sistienda.core.security.PasswordHasher;

import java.util.Locale;
import java.util.Objects;

public final class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;

    public AuthService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    public boolean requiereConfiguracionInicial() {
        return !usuarioRepository.existsActiveUser();
    }

    public Usuario crearDuenoInicial(String username, char[] password) {
        if (!requiereConfiguracionInicial()) {
            throw new ValidationException("La tienda ya tiene un usuario configurado.");
        }

        String normalizedUsername = validarUsername(username);
        validarPassword(password);
        String hash = passwordHasher.hash(password);
        return usuarioRepository.createOwner(normalizedUsername, hash);
    }

    public Usuario iniciarSesion(String username, char[] password) {
        String normalizedUsername = validarUsername(username);
        if (password == null || password.length == 0) {
            throw new ValidationException("Ingresá tu contraseña.");
        }

        var credential = usuarioRepository.findActiveByUsername(normalizedUsername)
                .orElseThrow(() -> new ValidationException("Usuario o contraseña incorrectos."));

        if (!passwordHasher.verify(password, credential.passwordHash())) {
            throw new ValidationException("Usuario o contraseña incorrectos.");
        }

        return credential.toUsuario();
    }

    private String validarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Ingresá tu usuario.");
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 50) {
            throw new ValidationException("El usuario debe tener entre 3 y 50 caracteres.");
        }
        if (!normalized.matches("[a-z0-9._-]+")) {
            throw new ValidationException("Usá sólo letras, números, punto, guion o guion bajo en el usuario.");
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
