package py.sistienda.core.security;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.RolUsuario;
import py.sistienda.core.model.Usuario;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

public final class AutorizacionService {

    private static final Map<RolUsuario, EnumSet<Permiso>> MATRIZ = Map.of(
            RolUsuario.DUENIO, EnumSet.allOf(Permiso.class),
            RolUsuario.ADMINISTRADOR, EnumSet.of(
                    Permiso.CATALOGO_VER,
                    Permiso.CATALOGO_GESTIONAR,
                    Permiso.COSTOS_VER,
                    Permiso.STOCK_GESTIONAR,
                    Permiso.ETIQUETAS_IMPRIMIR,
                    Permiso.CAJA_OPERAR,
                    Permiso.CAJA_MOVIMIENTOS,
                    Permiso.ARQUEO_VER,
                    Permiso.REPORTES_VER,
                    Permiso.COMPRAS_GESTIONAR
            ),
            RolUsuario.CAJERO, EnumSet.of(
                    Permiso.CATALOGO_VER,
                    Permiso.CAJA_OPERAR,
                    Permiso.CAJA_MOVIMIENTOS
            ),
            RolUsuario.VENDEDOR, EnumSet.of(
                    Permiso.CATALOGO_VER,
                    Permiso.CAJA_OPERAR
            )
    );

    public boolean puede(Usuario usuario, Permiso permiso) {
        Objects.requireNonNull(usuario);
        Objects.requireNonNull(permiso);
        if (!usuario.activo()) {
            return false;
        }
        return MATRIZ.getOrDefault(usuario.rolUsuario(), EnumSet.noneOf(Permiso.class)).contains(permiso);
    }

    public void exigir(Usuario usuario, Permiso permiso) {
        if (!puede(usuario, permiso)) {
            throw new ValidationException("Tu usuario no tiene permiso para realizar esta acción.");
        }
    }
}
