package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.DashboardReporte;
import py.sistienda.core.model.FiltroReporte;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.ReporteFiltroOpcion;
import py.sistienda.core.model.ReporteDiario;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaResumen;
import py.sistienda.core.repository.ReporteRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public final class ReporteService {

    private final ReporteRepository reporteRepository;

    public ReporteService(ReporteRepository reporteRepository) {
        this.reporteRepository = Objects.requireNonNull(reporteRepository);
    }

    public ReporteDiario resumenDiario(LocalDate fecha) {
        return reporteRepository.resumenDiario(Objects.requireNonNull(fecha));
    }

    public List<VentaResumen> listarVentas(LocalDate fecha) {
        return reporteRepository.listarVentas(Objects.requireNonNull(fecha));
    }

    public DashboardReporte dashboard(LocalDate desde, LocalDate hasta, MetodoPago metodoPago) {
        return dashboard(FiltroReporte.basico(desde, hasta, metodoPago));
    }

    public DashboardReporte dashboard(FiltroReporte filtro) {
        Objects.requireNonNull(filtro);
        validarRango(filtro.desde(), filtro.hasta());
        return new DashboardReporte(
                reporteRepository.resumenPeriodo(filtro),
                reporteRepository.lineaTiempo(filtro),
                reporteRepository.productosMasVendidos(filtro, 10),
                reporteRepository.listarVentas(filtro, 500)
        );
    }

    public List<ReporteFiltroOpcion> clientesDisponibles() {
        return reporteRepository.clientesDisponibles();
    }

    public List<ReporteFiltroOpcion> productosDisponibles() {
        return reporteRepository.productosDisponibles();
    }

    public VentaDetalle detalleVenta(long ventaId) {
        if (ventaId <= 0) {
            throw new IllegalArgumentException("La venta debe ser válida.");
        }
        return reporteRepository.detalleVenta(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la venta."));
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        Objects.requireNonNull(desde, "La fecha desde es obligatoria.");
        Objects.requireNonNull(hasta, "La fecha hasta es obligatoria.");
        if (hasta.isBefore(desde)) {
            throw new ValidationException("La fecha hasta no puede ser anterior a la fecha desde.");
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta);
        if (dias > 3660) {
            throw new ValidationException("El reporte admite períodos de hasta 10 años.");
        }
    }
}
