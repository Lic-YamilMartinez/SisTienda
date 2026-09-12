package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.AbonoClienteResultado;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ClienteCuentaMovimiento;
import py.sistienda.core.model.ClienteCuentaResumen;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaResultado;
import py.sistienda.core.repository.VentaRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VentaService {

    private static final double EPSILON = 0.000001d;
    private final VentaRepository ventaRepository;
    private final ClienteService clienteService;

    public VentaService(VentaRepository ventaRepository) {
        this(ventaRepository, null);
    }

    public VentaService(VentaRepository ventaRepository, ClienteService clienteService) {
        this.ventaRepository = Objects.requireNonNull(ventaRepository);
        this.clienteService = clienteService;
    }

    public VentaResultado vender(
            Usuario usuario,
            CajaSesion caja,
            List<LineaVenta> lineas,
            MetodoPago metodoPago,
            double recibido
    ) {
        return vender(usuario, caja, lineas, metodoPago, recibido, null);
    }

    public VentaResultado vender(
            Usuario usuario,
            CajaSesion caja,
            List<LineaVenta> lineas,
            MetodoPago metodoPago,
            double recibido,
            Cliente cliente
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
        validarStockAcumulado(lineas);

        double total = lineas.stream().mapToDouble(LineaVenta::subtotal).sum();
        if (!Double.isFinite(total) || total <= 0) {
            throw new ValidationException("El total de la venta debe ser mayor a cero.");
        }

        Long clienteId = null;
        if (metodoPago == MetodoPago.FIADO) {
            if (!puedeFiado(usuario)) {
                throw new ValidationException("Tu usuario no tiene permiso para vender a crédito.");
            }
            if (cliente == null) {
                throw new ValidationException("Seleccioná el cliente de la venta a crédito.");
            }
            Cliente vigente = clienteService.obtener(usuario, cliente.id());
            if (!vigente.activo()) {
                throw new ValidationException("El cliente seleccionado está inactivo.");
            }
            clienteId = vigente.id();
        } else if (cliente != null) {
            throw new ValidationException("El cliente sólo se asocia cuando la venta es fiada o a crédito.");
        }

        double recibidoNormalizado;
        double vuelto;
        if (metodoPago == MetodoPago.EFECTIVO) {
            if (!Double.isFinite(recibido) || recibido < total) {
                throw new ValidationException("El efectivo recibido debe cubrir el total de la venta.");
            }
            recibidoNormalizado = recibido;
            vuelto = recibido - total;
        } else if (metodoPago == MetodoPago.FIADO) {
            recibidoNormalizado = 0;
            vuelto = 0;
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
                clienteId,
                List.copyOf(lineas)
        );
    }

    public boolean puedeFiado(Usuario usuario) {
        return clienteService != null && clienteService.puedeGestionar(usuario);
    }

    public List<ClienteCuentaResumen> buscarClientes(Usuario usuario, String query) {
        exigirModuloFiado();
        return clienteService.buscar(usuario, query);
    }

    public Cliente crearCliente(Usuario usuario, String nombre, String documento, String telefono,
                                String direccion, String nota) {
        exigirModuloFiado();
        return clienteService.crear(usuario, nombre, documento, telefono, direccion, nota);
    }

    public Cliente actualizarCliente(Usuario usuario, long clienteId, String nombre, String documento,
                                     String telefono, String direccion, String nota) {
        exigirModuloFiado();
        return clienteService.actualizar(usuario, clienteId, nombre, documento, telefono, direccion, nota);
    }

    public double saldoCliente(Usuario usuario, long clienteId) {
        exigirModuloFiado();
        return clienteService.saldo(usuario, clienteId);
    }

    public List<ClienteCuentaMovimiento> movimientosCliente(Usuario usuario, long clienteId) {
        exigirModuloFiado();
        return clienteService.movimientos(usuario, clienteId);
    }

    public AbonoClienteResultado registrarAbonoCliente(Usuario usuario, CajaSesion caja, Cliente cliente,
                                                       MetodoPago metodoPago, double monto, String observacion) {
        exigirModuloFiado();
        return clienteService.registrarAbono(usuario, caja, cliente, metodoPago, monto, observacion);
    }

    private void exigirModuloFiado() {
        if (clienteService == null) {
            throw new ValidationException("El módulo de clientes y fiado no está disponible.");
        }
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

    private void validarStockAcumulado(List<LineaVenta> lineas) {
        Map<Long, Double> cantidades = new HashMap<>();
        Map<Long, Producto> productos = new HashMap<>();

        for (LineaVenta linea : lineas) {
            long productoId = linea.producto().id();
            cantidades.merge(productoId, linea.cantidad(), Double::sum);
            productos.putIfAbsent(productoId, linea.producto());
        }

        for (var entry : cantidades.entrySet()) {
            Producto producto = productos.get(entry.getKey());
            if (entry.getValue() - producto.stockActual() > EPSILON) {
                throw new ValidationException("Stock insuficiente para “" + producto.nombre() + "”.");
            }
        }
    }
}
