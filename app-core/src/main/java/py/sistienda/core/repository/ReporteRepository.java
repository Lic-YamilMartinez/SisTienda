package py.sistienda.core.repository;

import py.sistienda.core.model.FiltroReporte;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.ProductoVendidoResumen;
import py.sistienda.core.model.ReporteFiltroOpcion;
import py.sistienda.core.model.ReporteDiario;
import py.sistienda.core.model.ReporteLineaTiempo;
import py.sistienda.core.model.ReportePeriodoResumen;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaResumen;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReporteRepository {

    ReporteDiario resumenDiario(LocalDate fecha);

    List<VentaResumen> listarVentas(LocalDate fecha);

    Optional<VentaDetalle> detalleVenta(long ventaId);

    ReportePeriodoResumen resumenPeriodo(FiltroReporte filtro);

    List<ReporteLineaTiempo> lineaTiempo(FiltroReporte filtro);

    List<ProductoVendidoResumen> productosMasVendidos(FiltroReporte filtro, int limite);

    List<VentaResumen> listarVentas(FiltroReporte filtro, int limite);

    List<ReporteFiltroOpcion> clientesDisponibles();

    List<ReporteFiltroOpcion> productosDisponibles();

    ReportePeriodoResumen resumenPeriodo(LocalDate desde, LocalDate hasta, MetodoPago metodoPago);

    List<ReporteLineaTiempo> lineaTiempo(LocalDate desde, LocalDate hasta, MetodoPago metodoPago);

    List<ProductoVendidoResumen> productosMasVendidos(LocalDate desde, LocalDate hasta, MetodoPago metodoPago, int limite);

    List<VentaResumen> listarVentas(LocalDate desde, LocalDate hasta, MetodoPago metodoPago, int limite);
}
