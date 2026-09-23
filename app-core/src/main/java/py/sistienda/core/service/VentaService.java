package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.AbonoClienteResultado;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ClienteCuentaMovimiento;
import py.sistienda.core.model.ClienteCuentaResumen;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.PagoVenta;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaResultado;
import py.sistienda.core.repository.VentaRepository;
import py.sistienda.core.util.MoneyMath;

import java.util.ArrayList;
import java.util.EnumMap;
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
        if (metodoPago == MetodoPago.MIXTO) {
            throw new ValidationException("Usá la distribución de pago para registrar una venta mixta.");
        }

        for (LineaVenta linea : lineas) {
            validarLinea(linea);
        }
        validarStockAcumulado(lineas);

        double total = MoneyMath.guaranies(lineas.stream().mapToDouble(LineaVenta::subtotal).sum());
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
            recibidoNormalizado = MoneyMath.guaranies(recibido);
            vuelto = MoneyMath.guaranies(recibidoNormalizado - total);
        } else if (metodoPago == MetodoPago.FIADO) {
            recibidoNormalizado = 0;
            vuelto = 0;
        } else {
            recibidoNormalizado = MoneyMath.guaranies(total);
            vuelto = 0;
        }

        List<PagoVenta> pagos = List.of(new PagoVenta(metodoPago, total));
        return ventaRepository.register(
                caja.id(),
                usuario.id(),
                pagos,
                recibidoNormalizado,
                vuelto,
                clienteId,
                List.copyOf(lineas)
        );
    }

    public VentaResultado venderMixto(
            Usuario usuario,
            CajaSesion caja,
            List<LineaVenta> lineas,
            List<PagoVenta> pagos,
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
        for (LineaVenta linea : lineas) validarLinea(linea);
        validarStockAcumulado(lineas);

        double total = MoneyMath.guaranies(lineas.stream().mapToDouble(LineaVenta::subtotal).sum());
        if (!Double.isFinite(total) || total <= 0) {
            throw new ValidationException("El total de la venta debe ser mayor a cero.");
        }

        List<PagoVenta> normalizados = normalizarPagos(pagos);
        double totalPagos = MoneyMath.guaranies(normalizados.stream().mapToDouble(PagoVenta::monto).sum());
        if (Math.abs(totalPagos - total) > EPSILON) {
            throw new ValidationException("La distribución del pago debe completar exactamente " + (long) total + " Gs.");
        }

        double fiado = normalizados.stream()
                .filter(pago -> pago.metodoPago() == MetodoPago.FIADO)
                .mapToDouble(PagoVenta::monto)
                .sum();
        Long clienteId = null;
        if (fiado > EPSILON) {
            if (!puedeFiado(usuario)) {
                throw new ValidationException("Tu usuario no tiene permiso para dejar saldo fiado.");
            }
            if (cliente == null) {
                throw new ValidationException("Seleccioná el cliente que quedará debiendo.");
            }
            Cliente vigente = clienteService.obtener(usuario, cliente.id());
            if (!vigente.activo()) {
                throw new ValidationException("El cliente seleccionado está inactivo.");
            }
            clienteId = vigente.id();
        } else if (cliente != null) {
            throw new ValidationException("No hace falta asociar un cliente cuando no queda saldo fiado.");
        }

        double efectivo = normalizados.stream()
                .filter(pago -> pago.metodoPago() == MetodoPago.EFECTIVO)
                .mapToDouble(PagoVenta::monto)
                .sum();

        return ventaRepository.register(
                caja.id(),
                usuario.id(),
                normalizados,
                MoneyMath.guaranies(efectivo),
                0d,
                clienteId,
                List.copyOf(lineas)
        );
    }

    private List<PagoVenta> normalizarPagos(List<PagoVenta> pagos) {
        if (pagos == null || pagos.isEmpty()) {
            throw new ValidationException("Distribuí el total entre al menos una forma de pago.");
        }
        Map<MetodoPago, Double> acumulados = new EnumMap<>(MetodoPago.class);
        for (PagoVenta pago : pagos) {
            if (pago == null || pago.metodoPago() == MetodoPago.MIXTO) {
                throw new ValidationException("La distribución contiene una forma de pago inválida.");
            }
            acumulados.merge(
                    pago.metodoPago(),
                    MoneyMath.guaranies(pago.monto()),
                    (a, b) -> MoneyMath.guaranies(a + b)
            );
        }
        List<PagoVenta> result = new ArrayList<>();
        for (MetodoPago metodo : List.of(
                MetodoPago.EFECTIVO,
                MetodoPago.TRANSFERENCIA,
                MetodoPago.TARJETA,
                MetodoPago.FIADO
        )) {
            double monto = acumulados.getOrDefault(metodo, 0d);
            if (monto > EPSILON) result.add(new PagoVenta(metodo, monto));
        }
        if (result.isEmpty()) {
            throw new ValidationException("La distribución del pago está vacía.");
        }
        return List.copyOf(result);
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
