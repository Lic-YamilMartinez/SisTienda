package py.sistienda.core.security;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.RolUsuario;
import py.sistienda.core.model.Usuario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutorizacionServiceTest {

    private final AutorizacionService service = new AutorizacionService();

    @Test
    void duenoTieneAccesoTotal() {
        Usuario dueno = user(RolUsuario.DUENIO, true);
        for (Permiso permiso : Permiso.values()) {
            assertTrue(service.puede(dueno, permiso), permiso.name());
        }
    }

    @Test
    void administradorOperaNegocioPeroNoGestionaSeguridad() {
        Usuario admin = user(RolUsuario.ADMINISTRADOR, true);
        assertTrue(service.puede(admin, Permiso.COSTOS_VER));
        assertTrue(service.puede(admin, Permiso.COMPRAS_GESTIONAR));
        assertTrue(service.puede(admin, Permiso.REPORTES_VER));
        assertFalse(service.puede(admin, Permiso.CONFIGURACION_GESTIONAR));
        assertFalse(service.puede(admin, Permiso.USUARIOS_GESTIONAR));
    }

    @Test
    void cajeroNoVeCostosNiReportes() {
        Usuario cajero = user(RolUsuario.CAJERO, true);
        assertTrue(service.puede(cajero, Permiso.CAJA_OPERAR));
        assertTrue(service.puede(cajero, Permiso.CAJA_MOVIMIENTOS));
        assertFalse(service.puede(cajero, Permiso.COSTOS_VER));
        assertFalse(service.puede(cajero, Permiso.REPORTES_VER));
        assertFalse(service.puede(cajero, Permiso.COMPRAS_GESTIONAR));
    }

    @Test
    void vendedorSoloConsultaCatalogoYVende() {
        Usuario vendedor = user(RolUsuario.VENDEDOR, true);
        assertTrue(service.puede(vendedor, Permiso.CATALOGO_VER));
        assertTrue(service.puede(vendedor, Permiso.CAJA_OPERAR));
        assertFalse(service.puede(vendedor, Permiso.CAJA_MOVIMIENTOS));
        assertFalse(service.puede(vendedor, Permiso.STOCK_GESTIONAR));
        assertFalse(service.puede(vendedor, Permiso.COSTOS_VER));
    }

    @Test
    void usuarioInactivoNoTienePermisos() {
        Usuario inactivo = user(RolUsuario.DUENIO, false);
        assertFalse(service.puede(inactivo, Permiso.CATALOGO_VER));
        assertThrows(ValidationException.class,
                () -> service.exigir(inactivo, Permiso.CATALOGO_VER));
    }

    private Usuario user(RolUsuario rol, boolean activo) {
        return new Usuario(1L, "usuario", rol.name(), activo);
    }
}
