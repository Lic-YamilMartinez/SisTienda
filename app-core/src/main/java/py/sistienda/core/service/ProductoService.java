package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.repository.ProductoRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ProductoService {

    private final ProductoRepository productoRepository;
    private final CodigoBarrasService codigoBarrasService;

    public ProductoService(ProductoRepository productoRepository) {
        this(productoRepository, new CodigoBarrasService());
    }

    public ProductoService(ProductoRepository productoRepository, CodigoBarrasService codigoBarrasService) {
        this.productoRepository = Objects.requireNonNull(productoRepository);
        this.codigoBarrasService = Objects.requireNonNull(codigoBarrasService);
    }

    public List<Producto> listarActivos() {
        return productoRepository.findAllActive();
    }

    public Optional<Producto> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        return productoRepository.findByBarcode(codigo.trim());
    }

    public Optional<Producto> buscarPorPlu(int plu) {
        if (plu < 0 || plu > 99_999) return Optional.empty();
        return productoRepository.findByPlu(plu);
    }

    public Producto crear(String nombre, Long categoriaId, String categoriaNombre,
                           UnidadMedida unidadMedida, double precioVenta, double costo) {
        return crear(nombre, categoriaId, categoriaNombre, unidadMedida, precioVenta, costo, null, null);
    }

    public Producto crear(String nombre, Long categoriaId, String categoriaNombre,
                           UnidadMedida unidadMedida, double precioVenta, double costo,
                           String codigoBarras, Integer pluBalanza) {
        validar(nombre, unidadMedida, precioVenta, costo, codigoBarras, pluBalanza);
        Producto nuevo = new Producto(0L, normalizarNombre(nombre), categoriaId, categoriaNombre,
                unidadMedida, precioVenta, costo, 0d, true,
                normalizarCodigo(codigoBarras), normalizarPlu(pluBalanza));
        Producto creado = productoRepository.create(nuevo);
        return completarIdentificacion(creado);
    }

    public Producto actualizar(Producto actual, String nombre, Long categoriaId, String categoriaNombre,
                               UnidadMedida unidadMedida, double precioVenta, double costo) {
        return actualizar(actual, nombre, categoriaId, categoriaNombre, unidadMedida, precioVenta, costo,
                actual.codigoBarras(), actual.pluBalanza());
    }

    public Producto actualizar(Producto actual, String nombre, Long categoriaId, String categoriaNombre,
                               UnidadMedida unidadMedida, double precioVenta, double costo,
                               String codigoBarras, Integer pluBalanza) {
        Objects.requireNonNull(actual);
        validar(nombre, unidadMedida, precioVenta, costo, codigoBarras, pluBalanza);

        if (actual.unidadMedida() != unidadMedida && actual.stockActual() != 0d) {
            throw new ValidationException("Para cambiar entre unidad y kilogramo, primero dejá el stock en cero.");
        }

        Producto actualizado = new Producto(actual.id(), normalizarNombre(nombre), categoriaId,
                categoriaNombre, unidadMedida, precioVenta, costo, actual.stockActual(), actual.activo(),
                normalizarCodigo(codigoBarras), normalizarPlu(pluBalanza));
        return productoRepository.update(completarIdentificacionSinPersistir(actualizado));
    }

    public Producto asegurarIdentificacion(Producto producto) {
        Objects.requireNonNull(producto);
        Producto identificado = completarIdentificacionSinPersistir(producto);
        if (Objects.equals(identificado.codigoBarras(), producto.codigoBarras())
                && Objects.equals(identificado.pluBalanza(), producto.pluBalanza())) {
            return producto;
        }
        return productoRepository.update(identificado);
    }

    public void desactivar(long productoId) {
        if (productoId <= 0) {
            throw new ValidationException("Producto inválido.");
        }
        Producto producto = productoRepository.findAllActive().stream()
                .filter(item -> item.id() == productoId)
                .findFirst()
                .orElseThrow(() -> new ValidationException("El producto ya no está disponible."));
        desactivar(producto);
    }

    public void desactivar(Producto producto) {
        Objects.requireNonNull(producto);
        if (producto.stockActual() > 0d) {
            throw new ValidationException("No podés desactivar un producto que todavía tiene stock. Registrá una salida o dejalo activo hasta agotarlo.");
        }
        productoRepository.deactivate(producto.id());
    }

    private Producto completarIdentificacion(Producto producto) {
        Producto identificado = completarIdentificacionSinPersistir(producto);
        if (Objects.equals(identificado.codigoBarras(), producto.codigoBarras())
                && Objects.equals(identificado.pluBalanza(), producto.pluBalanza())) {
            return producto;
        }
        return productoRepository.update(identificado);
    }

    private Producto completarIdentificacionSinPersistir(Producto producto) {
        String codigo = producto.codigoBarras();
        Integer plu = producto.pluBalanza();
        if (producto.unidadMedida() == UnidadMedida.UN && (codigo == null || codigo.isBlank())) {
            codigo = codigoBarrasService.generarCodigoInterno(producto.id());
        }
        if (producto.unidadMedida() == UnidadMedida.KG && plu == null) {
            if (producto.id() > 99_999) {
                throw new ValidationException("Asigná manualmente un PLU de balanza a este producto.");
            }
            plu = Math.toIntExact(producto.id());
        }
        return new Producto(producto.id(), producto.nombre(), producto.categoriaId(), producto.categoriaNombre(),
                producto.unidadMedida(), producto.precioVenta(), producto.costo(), producto.stockActual(),
                producto.activo(), codigo, plu);
    }

    private void validar(String nombre, UnidadMedida unidadMedida, double precioVenta, double costo,
                         String codigoBarras, Integer pluBalanza) {
        if (nombre == null || nombre.isBlank()) {
            throw new ValidationException("Ingresá el nombre del producto.");
        }
        if (nombre.trim().length() > 120) {
            throw new ValidationException("El nombre del producto es demasiado largo.");
        }
        if (unidadMedida == null) {
            throw new ValidationException("Seleccioná cómo se vende el producto.");
        }
        if (!Double.isFinite(precioVenta) || precioVenta < 0) {
            throw new ValidationException("El precio de venta debe ser igual o mayor a cero.");
        }
        if (!Double.isFinite(costo) || costo < 0) {
            throw new ValidationException("El costo debe ser igual o mayor a cero.");
        }
        String codigo = normalizarCodigo(codigoBarras);
        if (codigo != null && !codigo.matches("[A-Za-z0-9._-]{3,64}")) {
            throw new ValidationException("El código de barras sólo puede contener letras, números, punto, guion o guion bajo.");
        }
        if (pluBalanza != null && (pluBalanza < 0 || pluBalanza > 99_999)) {
            throw new ValidationException("El PLU de balanza debe estar entre 0 y 99999.");
        }
    }

    private String normalizarNombre(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        return codigo.trim();
    }

    private Integer normalizarPlu(Integer plu) {
        return plu;
    }
}
