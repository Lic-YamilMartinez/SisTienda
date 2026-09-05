package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaResultado;
import py.sistienda.core.repository.VentaRepository;

import java.util.List;
import java.util.Objects;

public final class VentaService {

    private static final double EPSILON = 0.000001d;
    private final VentaRepository ventaRepository;

    public VentaService(VentaRepository ventaRepository) {
        this.ventaRepository = Objects.requireNonNull(ventaRepository);
    }

    public VentaResultado vender(
            Usuario usuario,
            CajaSesion caja,
            List<LineaVenta> lineas,
            MetodoPago metodoPago,
            double recibido
    ) {
        Objects.requireNonNull(usuario);
        Objects.requireNonNull(caja);
        Objects.requireNonNull(lineas);

        if (!caja.abierta()) {
            throw new ValidationException("Abrí la caja antes de registrar una venta.");
        }
        if (caja.usuarioId() != usuario.id()) {
            throw new ValidationException("La caja abierta pertenece a otro usuario.");
        }
        if (lineas.isEmpty()) {
            throw new ValidationException("Agregá al menos un producto a la venta.");
        }
        if (metodoPago == null) {
            throw new ValidationException("Seleccioná un método de pago.");
        }

        for (LineaVenta linea : lineas) {
            validarLinea(linea);
        }

        double total = lineas.stream().mapToDouble(LineaVenta::subtotal).sum();
        if (!Double.isFinite(total) || total <= 0) {
            throw new ValidationException("El total de la venta debe ser mayor a cero.");
        }

        double recibidoNormalizado;
        double vuelto;
        if (metodoPago == MetodoPago.EFECTIVO) {
            if (!Double.isFinite(recibido) || recibido < total) {
                throw new ValidationException("El efectivo recibido debe cubrir el total de la venta.");
            }
            recibidoNormalizado = recibido;
            vuelto = recibido - total;
        } else {
            recibidoNormalizado = total;
            vuelto = 0;
        }

        return ventaRepository.register(
                caja.id(),
                usuario.id(),
                metodoPago,
                recibidoNormalizado,
                vuelto,
                List.copyOf(lineas)
        );
    }

    private void validarLinea(LineaVenta linea) {
        if (linea == null || linea.producto() == null) {
            throw new ValidationException("Hay un producto inválido en la venta.");
        }
        if (!linea.producto().activo()) {
            throw new ValidationException("El producto “" + linea.producto().nombre() + "” está inactivo.");
        }
        if (!Double.isFinite(linea.cantidad()) || linea.cantidad() <= 0) {
            throw new ValidationException("La cantidad de “" + linea.producto().nombre() + "” debe ser mayor a cero.");
        }
        if (linea.producto().unidadMedida() == UnidadMedida.UN
                && Math.abs(linea.cantidad() - Math.rint(linea.cantidad())) > EPSILON) {
            throw new ValidationException("“" + linea.producto().nombre() + "” se vende por unidad y no acepta decimales.");
        }
        if (linea.cantidad() - linea.producto().stockActual() > EPSILON) {
            throw new ValidationException("Stock insuficiente para “" + linea.producto().nombre() + "”.");
        }
    }
}
