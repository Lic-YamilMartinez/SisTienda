package py.sistienda.core.model;

import java.util.Arrays;
import java.util.Objects;

public final class LogoNegocio {
    private final byte[] contenido;
    private final String mimeType;

    public LogoNegocio(byte[] contenido, String mimeType) {
        this.contenido = Objects.requireNonNull(contenido).clone();
        this.mimeType = Objects.requireNonNull(mimeType);
    }

    public byte[] contenido() {
        return contenido.clone();
    }

    public String mimeType() {
        return mimeType;
    }

    public int tamanioBytes() {
        return contenido.length;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LogoNegocio that)) return false;
        return mimeType.equals(that.mimeType) && Arrays.equals(contenido, that.contenido);
    }

    @Override
    public int hashCode() {
        return 31 * mimeType.hashCode() + Arrays.hashCode(contenido);
    }
}
