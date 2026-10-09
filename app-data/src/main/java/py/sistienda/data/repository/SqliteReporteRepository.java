package py.sistienda.data.repository;

import py.sistienda.core.model.FiltroReporte;
import py.sistienda.core.model.GranularidadReporte;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.PagoVenta;
import py.sistienda.core.model.ProductoVendidoResumen;
import py.sistienda.core.model.ReporteDiario;
import py.sistienda.core.model.ReporteFiltroOpcion;
import py.sistienda.core.model.ReporteLineaTiempo;
import py.sistienda.core.model.ReportePeriodoResumen;
import py.sistienda.core.model.TipoVentaReporte;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaDetalleItem;
import py.sistienda.core.model.VentaResumen;
import py.sistienda.core.repository.ReporteRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SqliteReporteRepository implements ReporteRepository {

    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SqliteConnectionFactory connectionFactory;

    public SqliteReporteRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public ReporteDiario resumenDiario(LocalDate fecha) {
        String sql = """
                WITH filtro AS (SELECT ? AS fecha),
                comerciales AS (
                    SELECT v.fecha, v.total AS ventas, v.ganancia_total AS ganancia, 1 AS ticket
                    FROM venta v
                    WHERE v.anulada = 0
                      AND date(v.fecha, 'localtime') = (SELECT fecha FROM filtro)
                    UNION ALL
                    SELECT d.fecha, -d.total AS ventas, -d.ganancia_revertida AS ganancia, 0 AS ticket
                    FROM devolucion d
                    WHERE date(d.fecha, 'localtime') = (SELECT fecha FROM filtro)
                ),
                pagos AS (
                    SELECT v.fecha, vp.metodo_pago, vp.monto AS importe
                    FROM venta v
                    JOIN venta_pago vp ON vp.venta_id = v.id
                    WHERE v.anulada = 0
                      AND date(v.fecha, 'localtime') = (SELECT fecha FROM filtro)
                    UNION ALL
                    SELECT d.fecha, dp.metodo_pago, -dp.monto AS importe
                    FROM devolucion d
                    JOIN devolucion_pago dp ON dp.devolucion_id = d.id
                    WHERE date(d.fecha, 'localtime') = (SELECT fecha FROM filtro)
                )
                SELECT
                    COALESCE((SELECT SUM(ventas) FROM comerciales), 0) AS ventas,
                    COALESCE((SELECT SUM(ganancia) FROM comerciales), 0) AS ganancia,
                    COALESCE((SELECT SUM(ticket) FROM comerciales), 0) AS tickets,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'EFECTIVO' THEN importe ELSE 0 END) FROM pagos), 0) AS efectivo,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'TRANSFERENCIA' THEN importe ELSE 0 END) FROM pagos), 0) AS transferencia,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'TARJETA' THEN importe ELSE 0 END) FROM pagos), 0) AS tarjeta,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'FIADO' THEN importe ELSE 0 END) FROM pagos), 0) AS fiado
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, fecha.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return new ReporteDiario(fecha, 0, 0, 0, 0, 0, 0, 0, 0);
                }
                double ventas = result.getDouble("ventas");
                long tickets = result.getLong("tickets");
                return new ReporteDiario(
                        fecha,
                        ventas,
                        result.getDouble("ganancia"),
                        tickets,
                        tickets == 0 ? 0d : ventas / tickets,
                        result.getDouble("efectivo"),
                        result.getDouble("transferencia"),
                        result.getDouble("tarjeta"),
                        result.getDouble("fiado")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el resumen diario.", e);
        }
    }

    @Override
    public List<VentaResumen> listarVentas(LocalDate fecha) {
        return listarVentas(fecha, fecha, null, 1000);
    }

    @Override
    public ReportePeriodoResumen resumenPeriodo(FiltroReporte filtro) {
        SqlPlan movimientos = movimientosPlan(filtro);
        String resumenSql = """
                WITH movimientos AS (
                """ + movimientos.sql() + """
                )
                SELECT
                    COALESCE(SUM(ventas), 0) AS ventas,
                    COALESCE(SUM(costo), 0) AS costo,
                    COALESCE(SUM(ganancia), 0) AS ganancia,
                    COUNT(DISTINCT CASE WHEN es_venta = 1 THEN venta_id END) AS tickets
                FROM movimientos
                """;

        SqlPlan pagos = pagosPlan(filtro);
        String pagosSql = """
                WITH pagos AS (
                """ + pagos.sql() + """
                )
                SELECT
                    COALESCE(SUM(CASE WHEN metodo_pago = 'EFECTIVO' THEN importe ELSE 0 END), 0) AS efectivo,
                    COALESCE(SUM(CASE WHEN metodo_pago = 'TRANSFERENCIA' THEN importe ELSE 0 END), 0) AS transferencia,
                    COALESCE(SUM(CASE WHEN metodo_pago = 'TARJETA' THEN importe ELSE 0 END), 0) AS tarjeta,
                    COALESCE(SUM(CASE WHEN metodo_pago = 'FIADO' THEN importe ELSE 0 END), 0) AS fiado
                FROM pagos
                """;

        String globalSql = """
                WITH ganancias AS (
                    SELECT fecha, ganancia_total AS ganancia
                    FROM venta
                    WHERE anulada = 0
                    UNION ALL
                    SELECT fecha, -ganancia_revertida AS ganancia
                    FROM devolucion
                )
                SELECT
                    COALESCE((SELECT SUM(ganancia) FROM ganancias
                              WHERE date(fecha, 'localtime') BETWEEN ? AND ?), 0) AS ganancia_global,
                    COALESCE((SELECT SUM(CASE
                              WHEN tipo = 'INGRESO' AND categoria <> 'Cobro de fiado' THEN monto
                              ELSE 0 END)
                              FROM caja_movimiento
                              WHERE date(fecha, 'localtime') BETWEEN ? AND ?), 0) AS ingresos,
                    COALESCE((SELECT SUM(CASE WHEN tipo = 'EGRESO' THEN monto ELSE 0 END)
                              FROM caja_movimiento
                              WHERE date(fecha, 'localtime') BETWEEN ? AND ?), 0) AS egresos
                """;

        try (var connection = connectionFactory.open()) {
            double ventas;
            double costo;
            double ganancia;
            long tickets;
            try (var statement = connection.prepareStatement(resumenSql)) {
                bind(statement, movimientos.params(), 1);
                try (var result = statement.executeQuery()) {
                    result.next();
                    ventas = result.getDouble("ventas");
                    costo = result.getDouble("costo");
                    ganancia = result.getDouble("ganancia");
                    tickets = result.getLong("tickets");
                }
            }

            double efectivo;
            double transferencia;
            double tarjeta;
            double fiado;
            try (var statement = connection.prepareStatement(pagosSql)) {
                bind(statement, pagos.params(), 1);
                try (var result = statement.executeQuery()) {
                    result.next();
                    efectivo = result.getDouble("efectivo");
                    transferencia = result.getDouble("transferencia");
                    tarjeta = result.getDouble("tarjeta");
                    fiado = result.getDouble("fiado");
                }
            }

            double gananciaGlobal;
            double ingresos;
            double egresos;
            try (var statement = connection.prepareStatement(globalSql)) {
                int index = 1;
                for (int i = 0; i < 3; i++) {
                    statement.setString(index++, filtro.desde().toString());
                    statement.setString(index++, filtro.hasta().toString());
                }
                try (var result = statement.executeQuery()) {
                    result.next();
                    gananciaGlobal = result.getDouble("ganancia_global");
                    ingresos = result.getDouble("ingresos");
                    egresos = result.getDouble("egresos");
                }
            }

            return new ReportePeriodoResumen(
                    filtro.desde(),
                    filtro.hasta(),
                    filtro.metodoPago(),
                    ventas,
                    costo,
                    ganancia,
                    tickets,
                    tickets == 0 ? 0d : ventas / tickets,
                    efectivo,
                    transferencia,
                    tarjeta,
                    fiado,
                    ingresos,
                    egresos,
                    gananciaGlobal + ingresos - egresos
            );
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el resumen ejecutivo del período.", e);
        }
    }

    @Override
    public List<ReporteLineaTiempo> lineaTiempo(FiltroReporte filtro) {
        boolean mensual = esMensual(filtro);
        SqlPlan movimientos = movimientosPlan(filtro);
        String bucket = mensual
                ? "strftime('%Y-%m-01', fecha, 'localtime')"
                : "date(fecha, 'localtime')";
        String sql = ("""
                WITH movimientos AS (
                %s
                )
                SELECT %s AS periodo,
                       COALESCE(SUM(ventas), 0) AS ventas,
                       COALESCE(SUM(costo), 0) AS costo,
                       COALESCE(SUM(ganancia), 0) AS ganancia,
                       COUNT(DISTINCT CASE WHEN es_venta = 1 THEN venta_id END) AS tickets
                FROM movimientos
                GROUP BY periodo
                ORDER BY periodo
                """).formatted(movimientos.sql(), bucket);

        Map<LocalDate, ReporteLineaTiempo> encontrados = new LinkedHashMap<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            bind(statement, movimientos.params(), 1);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    LocalDate periodo = LocalDate.parse(result.getString("periodo"));
                    encontrados.put(periodo, new ReporteLineaTiempo(
                            periodo,
                            result.getDouble("ventas"),
                            result.getDouble("costo"),
                            result.getDouble("ganancia"),
                            result.getLong("tickets")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar la evolución ejecutiva del período.", e);
        }

        List<ReporteLineaTiempo> timeline = new ArrayList<>();
        if (mensual) {
            LocalDate cursor = filtro.desde().withDayOfMonth(1);
            LocalDate fin = filtro.hasta().withDayOfMonth(1);
            while (!cursor.isAfter(fin)) {
                timeline.add(encontrados.getOrDefault(
                        cursor, new ReporteLineaTiempo(cursor, 0, 0, 0, 0)
                ));
                cursor = cursor.plusMonths(1);
            }
        } else {
            LocalDate cursor = filtro.desde();
            while (!cursor.isAfter(filtro.hasta())) {
                timeline.add(encontrados.getOrDefault(
                        cursor, new ReporteLineaTiempo(cursor, 0, 0, 0, 0)
                ));
                cursor = cursor.plusDays(1);
            }
        }
        return List.copyOf(timeline);
    }

    @Override
    public List<ProductoVendidoResumen> productosMasVendidos(FiltroReporte filtro, int limite) {
        SqlPlan movimientos = movimientosPlan(filtro);
        String sql = """
                WITH movimientos AS (
                """ + movimientos.sql() + """
                )
                SELECT p.id AS producto_id,
                       p.nombre,
                       p.unidad_medida,
                       COALESCE(SUM(m.cantidad), 0) AS cantidad,
                       COALESCE(SUM(m.ventas), 0) AS ventas,
                       COALESCE(SUM(m.costo), 0) AS costo,
                       COALESCE(SUM(m.ganancia), 0) AS ganancia
                FROM movimientos m
                JOIN producto p ON p.id = m.producto_id
                GROUP BY p.id, p.nombre, p.unidad_medida
                HAVING ABS(SUM(m.ventas)) > 0.000001 OR ABS(SUM(m.cantidad)) > 0.000001
                ORDER BY ventas DESC, cantidad DESC
                LIMIT ?
                """;

        List<ProductoVendidoResumen> productos = new ArrayList<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            int index = bind(statement, movimientos.params(), 1);
            statement.setInt(index, Math.max(1, limite));
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    productos.add(new ProductoVendidoResumen(
                            result.getLong("producto_id"),
                            result.getString("nombre"),
                            UnidadMedida.valueOf(result.getString("unidad_medida")),
                            result.getDouble("cantidad"),
                            result.getDouble("ventas"),
                            result.getDouble("costo"),
                            result.getDouble("ganancia")
                    ));
                }
            }
            return List.copyOf(productos);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el ranking ejecutivo de productos.", e);
        }
    }

    @Override
    public List<VentaResumen> listarVentas(FiltroReporte filtro, int limite) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                WHERE date(v.fecha, 'localtime') BETWEEN ? AND ?
                """);
        params.add(filtro.desde().toString());
        params.add(filtro.hasta().toString());
        appendVentaFilters(where, params, filtro, "v");

        String sql = """
                SELECT v.id, v.nro_ticket, v.fecha, u.username, v.metodo_pago,
                       v.total, v.ganancia_total, v.anulada, c.nombre AS cliente,
                       COALESCE((SELECT SUM(dv.total) FROM devolucion dv WHERE dv.venta_id = v.id), 0) AS devuelto
                FROM venta v
                JOIN usuario u ON u.id = v.usuario_id
                LEFT JOIN cliente c ON c.id = v.cliente_id
                """ + where + """
                ORDER BY v.fecha DESC, v.id DESC
                LIMIT ?
                """;

        List<VentaResumen> ventas = new ArrayList<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            int index = bind(statement, params, 1);
            statement.setInt(index, Math.max(1, limite));
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    ventas.add(new VentaResumen(
                            result.getLong("id"),
                            result.getLong("nro_ticket"),
                            parseDate(result.getString("fecha")),
                            result.getString("username"),
                            MetodoPago.valueOf(result.getString("metodo_pago")),
                            result.getDouble("total"),
                            result.getDouble("ganancia_total"),
                            result.getDouble("devuelto"),
                            result.getInt("anulada") != 0,
                            result.getString("cliente")
                    ));
                }
            }
            return List.copyOf(ventas);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar el detalle ejecutivo de ventas.", e);
        }
    }

    @Override
    public List<ReporteFiltroOpcion> clientesDisponibles() {
        String sql = """
                SELECT DISTINCT c.id, c.nombre
                FROM cliente c
                JOIN venta v ON v.cliente_id = c.id
                ORDER BY c.nombre COLLATE NOCASE
                """;
        return cargarOpciones(sql);
    }

    @Override
    public List<ReporteFiltroOpcion> productosDisponibles() {
        String sql = """
                SELECT DISTINCT p.id, 'ID ' || p.id || ' · ' || p.nombre AS nombre
                FROM producto p
                JOIN venta_detalle d ON d.producto_id = p.id
                ORDER BY p.nombre COLLATE NOCASE
                """;
        return cargarOpciones(sql);
    }

    private List<ReporteFiltroOpcion> cargarOpciones(String sql) {
        List<ReporteFiltroOpcion> opciones = new ArrayList<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            while (result.next()) {
                opciones.add(new ReporteFiltroOpcion(
                        result.getLong("id"),
                        result.getString("nombre")
                ));
            }
            return List.copyOf(opciones);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudieron cargar los filtros del dashboard.", e);
        }
    }

    private SqlPlan movimientosPlan(FiltroReporte filtro) {
        boolean componentePago = filtro.metodoPago() != null && filtro.metodoPago() != MetodoPago.MIXTO;
        List<Object> params = new ArrayList<>();

        String saleJoin = componentePago ? " JOIN venta_pago vp ON vp.venta_id = v.id " : " ";
        String refundJoin = componentePago ? " JOIN devolucion_pago dp ON dp.devolucion_id = dv.id " : " ";

        String ventaFactor = componentePago
                ? "CASE WHEN v.total <= 0 THEN 0 ELSE vp.monto / v.total END"
                : "1.0";
        String devolucionFactor = componentePago
                ? "CASE WHEN dv.total <= 0 THEN 0 ELSE dp.monto / dv.total END"
                : "1.0";

        StringBuilder saleWhere = new StringBuilder("""
                WHERE v.anulada = 0
                  AND date(v.fecha, 'localtime') BETWEEN ? AND ?
                """);
        params.add(filtro.desde().toString());
        params.add(filtro.hasta().toString());
        if (componentePago) {
            saleWhere.append(" AND vp.metodo_pago = ? ");
            params.add(filtro.metodoPago().name());
        } else if (filtro.metodoPago() == MetodoPago.MIXTO) {
            saleWhere.append(" AND v.metodo_pago = 'MIXTO' ");
        }
        appendDimensionFilters(saleWhere, params, filtro, "v", "d");

        List<Object> refundParams = new ArrayList<>();
        StringBuilder refundWhere = new StringBuilder("""
                WHERE date(dv.fecha, 'localtime') BETWEEN ? AND ?
                """);
        refundParams.add(filtro.desde().toString());
        refundParams.add(filtro.hasta().toString());
        if (componentePago) {
            refundWhere.append(" AND dp.metodo_pago = ? ");
            refundParams.add(filtro.metodoPago().name());
        } else if (filtro.metodoPago() == MetodoPago.MIXTO) {
            refundWhere.append(" AND v.metodo_pago = 'MIXTO' ");
        }
        appendDimensionFilters(refundWhere, refundParams, filtro, "v", "dd");
        params.addAll(refundParams);

        String sql = ("""
                SELECT v.id AS venta_id,
                       NULL AS devolucion_id,
                       v.fecha AS fecha,
                       d.producto_id,
                       d.cantidad * (%s) AS cantidad,
                       d.subtotal * (%s) AS ventas,
                       (d.subtotal - d.ganancia_linea) * (%s) AS costo,
                       d.ganancia_linea * (%s) AS ganancia,
                       1 AS es_venta
                FROM venta_detalle d
                JOIN venta v ON v.id = d.venta_id
                %s
                %s
                UNION ALL
                SELECT dv.venta_id,
                       dv.id AS devolucion_id,
                       dv.fecha AS fecha,
                       dd.producto_id,
                       -dd.cantidad * (%s) AS cantidad,
                       -dd.subtotal * (%s) AS ventas,
                       -(dd.subtotal - dd.ganancia_revertida) * (%s) AS costo,
                       -dd.ganancia_revertida * (%s) AS ganancia,
                       0 AS es_venta
                FROM devolucion_detalle dd
                JOIN devolucion dv ON dv.id = dd.devolucion_id
                JOIN venta v ON v.id = dv.venta_id
                %s
                %s
                """).formatted(
                        ventaFactor, ventaFactor, ventaFactor, ventaFactor,
                        saleJoin, saleWhere,
                        devolucionFactor, devolucionFactor, devolucionFactor, devolucionFactor,
                        refundJoin, refundWhere
                );

        return new SqlPlan(sql, List.copyOf(params));
    }

    private SqlPlan pagosPlan(FiltroReporte filtro) {
        List<Object> params = new ArrayList<>();

        StringBuilder saleWhere = new StringBuilder("""
                WHERE v.anulada = 0
                  AND date(v.fecha, 'localtime') BETWEEN ? AND ?
                """);
        params.add(filtro.desde().toString());
        params.add(filtro.hasta().toString());
        if (filtro.metodoPago() != null && filtro.metodoPago() != MetodoPago.MIXTO) {
            saleWhere.append(" AND vp.metodo_pago = ? ");
            params.add(filtro.metodoPago().name());
        } else if (filtro.metodoPago() == MetodoPago.MIXTO) {
            saleWhere.append(" AND v.metodo_pago = 'MIXTO' ");
        }
        appendDimensionFilters(saleWhere, params, filtro, "v", "d");

        List<Object> refundParams = new ArrayList<>();
        StringBuilder refundWhere = new StringBuilder("""
                WHERE date(dv.fecha, 'localtime') BETWEEN ? AND ?
                """);
        refundParams.add(filtro.desde().toString());
        refundParams.add(filtro.hasta().toString());
        if (filtro.metodoPago() != null && filtro.metodoPago() != MetodoPago.MIXTO) {
            refundWhere.append(" AND dp.metodo_pago = ? ");
            refundParams.add(filtro.metodoPago().name());
        } else if (filtro.metodoPago() == MetodoPago.MIXTO) {
            refundWhere.append(" AND v.metodo_pago = 'MIXTO' ");
        }
        appendDimensionFilters(refundWhere, refundParams, filtro, "v", "dd");
        params.addAll(refundParams);

        String sql = """
                SELECT vp.metodo_pago,
                       CASE WHEN v.total <= 0 THEN 0
                            ELSE d.subtotal * vp.monto / v.total END AS importe
                FROM venta_detalle d
                JOIN venta v ON v.id = d.venta_id
                JOIN venta_pago vp ON vp.venta_id = v.id
                """ + saleWhere + """
                UNION ALL
                SELECT dp.metodo_pago,
                       CASE WHEN dv.total <= 0 THEN 0
                            ELSE -dd.subtotal * dp.monto / dv.total END AS importe
                FROM devolucion_detalle dd
                JOIN devolucion dv ON dv.id = dd.devolucion_id
                JOIN venta v ON v.id = dv.venta_id
                JOIN devolucion_pago dp ON dp.devolucion_id = dv.id
                """ + refundWhere;

        return new SqlPlan(sql, List.copyOf(params));
    }

    private void appendDimensionFilters(
            StringBuilder where,
            List<Object> params,
            FiltroReporte filtro,
            String ventaAlias,
            String detalleAlias
    ) {
        if (filtro.clienteId() != null) {
            where.append(" AND ").append(ventaAlias).append(".cliente_id = ? ");
            params.add(filtro.clienteId());
        }
        if (filtro.productoId() != null) {
            where.append(" AND ").append(detalleAlias).append(".producto_id = ? ");
            params.add(filtro.productoId());
        }
        appendTipoVentaFilter(where, filtro, ventaAlias);
    }

    private void appendVentaFilters(
            StringBuilder where,
            List<Object> params,
            FiltroReporte filtro,
            String ventaAlias
    ) {
        if (filtro.metodoPago() != null && filtro.metodoPago() != MetodoPago.MIXTO) {
            where.append(" AND EXISTS (SELECT 1 FROM venta_pago vpf")
                    .append(" WHERE vpf.venta_id = ").append(ventaAlias).append(".id")
                    .append(" AND vpf.metodo_pago = ?) ");
            params.add(filtro.metodoPago().name());
        } else if (filtro.metodoPago() == MetodoPago.MIXTO) {
            where.append(" AND ").append(ventaAlias).append(".metodo_pago = 'MIXTO' ");
        }
        if (filtro.clienteId() != null) {
            where.append(" AND ").append(ventaAlias).append(".cliente_id = ? ");
            params.add(filtro.clienteId());
        }
        if (filtro.productoId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM venta_detalle vdf")
                    .append(" WHERE vdf.venta_id = ").append(ventaAlias).append(".id")
                    .append(" AND vdf.producto_id = ?) ");
            params.add(filtro.productoId());
        }
        appendTipoVentaFilter(where, filtro, ventaAlias);
    }

    private void appendTipoVentaFilter(StringBuilder where, FiltroReporte filtro, String ventaAlias) {
        if (filtro.tipoVenta() == null || filtro.tipoVenta() == TipoVentaReporte.TODOS) return;
        String fiado = "EXISTS (SELECT 1 FROM venta_pago tvf WHERE tvf.venta_id = "
                + ventaAlias + ".id AND tvf.metodo_pago = 'FIADO')";
        String cobrado = "EXISTS (SELECT 1 FROM venta_pago tvc WHERE tvc.venta_id = "
                + ventaAlias + ".id AND tvc.metodo_pago <> 'FIADO')";
        switch (filtro.tipoVenta()) {
            case CONTADO -> where.append(" AND NOT ").append(fiado).append(" ");
            case CREDITO -> where.append(" AND ").append(fiado).append(" AND NOT ").append(cobrado).append(" ");
            case MIXTO_PARCIAL -> where.append(" AND ").append(fiado).append(" AND ").append(cobrado).append(" ");
            default -> {
            }
        }
    }

    private boolean esMensual(FiltroReporte filtro) {
        if (filtro.granularidad() == GranularidadReporte.MENSUAL) return true;
        if (filtro.granularidad() == GranularidadReporte.DIARIO) return false;
        return ChronoUnit.DAYS.between(filtro.desde(), filtro.hasta()) > 62;
    }

    private int bind(java.sql.PreparedStatement statement, List<Object> params, int start) throws SQLException {
        int index = start;
        for (Object value : params) {
            if (value instanceof Long number) statement.setLong(index++, number);
            else if (value instanceof Integer number) statement.setInt(index++, number);
            else statement.setString(index++, String.valueOf(value));
        }
        return index;
    }

    private record SqlPlan(String sql, List<Object> params) {
    }

    @Override
    public ReportePeriodoResumen resumenPeriodo(LocalDate desde, LocalDate hasta, MetodoPago metodoPago) {
        String filteredSql;
        if (metodoPago == null) {
            filteredSql = """
                    WITH movimientos AS (
                        SELECT v.fecha, v.total AS ventas,
                               v.total - v.ganancia_total AS costo,
                               v.ganancia_total AS ganancia, 1 AS ticket
                        FROM venta v
                        WHERE v.anulada = 0
                        UNION ALL
                        SELECT d.fecha, -d.total AS ventas,
                               -d.costo_total AS costo,
                               -d.ganancia_revertida AS ganancia, 0 AS ticket
                        FROM devolucion d
                    )
                    SELECT COALESCE(SUM(ventas), 0) AS ventas,
                           COALESCE(SUM(costo), 0) AS costo,
                           COALESCE(SUM(ganancia), 0) AS ganancia,
                           COALESCE(SUM(ticket), 0) AS tickets
                    FROM movimientos
                    WHERE date(fecha, 'localtime') BETWEEN ? AND ?
                    """;
        } else if (metodoPago == MetodoPago.MIXTO) {
            filteredSql = """
                    WITH movimientos AS (
                        SELECT v.fecha, v.total AS ventas,
                               v.total - v.ganancia_total AS costo,
                               v.ganancia_total AS ganancia, 1 AS ticket
                        FROM venta v
                        WHERE v.anulada = 0 AND v.metodo_pago = 'MIXTO'
                        UNION ALL
                        SELECT d.fecha, -d.total AS ventas,
                               -d.costo_total AS costo,
                               -d.ganancia_revertida AS ganancia, 0 AS ticket
                        FROM devolucion d
                        WHERE d.metodo_pago = 'MIXTO'
                    )
                    SELECT COALESCE(SUM(ventas), 0) AS ventas,
                           COALESCE(SUM(costo), 0) AS costo,
                           COALESCE(SUM(ganancia), 0) AS ganancia,
                           COALESCE(SUM(ticket), 0) AS tickets
                    FROM movimientos
                    WHERE date(fecha, 'localtime') BETWEEN ? AND ?
                    """;
        } else {
            filteredSql = """
                    WITH movimientos AS (
                        SELECT v.fecha,
                               vp.monto AS ventas,
                               CASE WHEN v.total <= 0 THEN 0
                                    ELSE (v.total - v.ganancia_total) * vp.monto / v.total END AS costo,
                               CASE WHEN v.total <= 0 THEN 0
                                    ELSE v.ganancia_total * vp.monto / v.total END AS ganancia,
                               1 AS ticket
                        FROM venta v
                        JOIN venta_pago vp ON vp.venta_id = v.id
                        WHERE v.anulada = 0 AND vp.metodo_pago = ?
                        UNION ALL
                        SELECT d.fecha,
                               -dp.monto AS ventas,
                               CASE WHEN d.total <= 0 THEN 0
                                    ELSE -d.costo_total * dp.monto / d.total END AS costo,
                               CASE WHEN d.total <= 0 THEN 0
                                    ELSE -d.ganancia_revertida * dp.monto / d.total END AS ganancia,
                               0 AS ticket
                        FROM devolucion d
                        JOIN devolucion_pago dp ON dp.devolucion_id = d.id
                        WHERE dp.metodo_pago = ?
                    )
                    SELECT COALESCE(SUM(ventas), 0) AS ventas,
                           COALESCE(SUM(costo), 0) AS costo,
                           COALESCE(SUM(ganancia), 0) AS ganancia,
                           COALESCE(SUM(ticket), 0) AS tickets
                    FROM movimientos
                    WHERE date(fecha, 'localtime') BETWEEN ? AND ?
                    """;
        }

        String globalSalesSql = """
                WITH pagos AS (
                    SELECT v.fecha, vp.metodo_pago, vp.monto AS importe
                    FROM venta v
                    JOIN venta_pago vp ON vp.venta_id = v.id
                    WHERE v.anulada = 0
                    UNION ALL
                    SELECT d.fecha, dp.metodo_pago, -dp.monto AS importe
                    FROM devolucion d
                    JOIN devolucion_pago dp ON dp.devolucion_id = d.id
                ),
                ganancias AS (
                    SELECT v.fecha, v.ganancia_total AS ganancia
                    FROM venta v WHERE v.anulada = 0
                    UNION ALL
                    SELECT d.fecha, -d.ganancia_revertida AS ganancia
                    FROM devolucion d
                )
                SELECT
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'EFECTIVO' THEN importe ELSE 0 END)
                              FROM pagos WHERE date(fecha, 'localtime') BETWEEN ? AND ?), 0) AS efectivo,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'TRANSFERENCIA' THEN importe ELSE 0 END)
                              FROM pagos WHERE date(fecha, 'localtime') BETWEEN ? AND ?), 0) AS transferencia,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'TARJETA' THEN importe ELSE 0 END)
                              FROM pagos WHERE date(fecha, 'localtime') BETWEEN ? AND ?), 0) AS tarjeta,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'FIADO' THEN importe ELSE 0 END)
                              FROM pagos WHERE date(fecha, 'localtime') BETWEEN ? AND ?), 0) AS fiado,
                    COALESCE((SELECT SUM(ganancia) FROM ganancias
                              WHERE date(fecha, 'localtime') BETWEEN ? AND ?), 0) AS ganancia_global
                """;

        String movementsSql = """
                SELECT
                    COALESCE(SUM(CASE
                        WHEN tipo = 'INGRESO' AND categoria <> 'Cobro de fiado' THEN monto
                        ELSE 0 END), 0) AS ingresos,
                    COALESCE(SUM(CASE WHEN tipo = 'EGRESO' THEN monto ELSE 0 END), 0) AS egresos
                FROM caja_movimiento
                WHERE date(fecha, 'localtime') BETWEEN ? AND ?
                """;

        try (var connection = connectionFactory.open()) {
            double ventas;
            double costo;
            double ganancia;
            long tickets;
            try (var statement = connection.prepareStatement(filteredSql)) {
                int index = 1;
                if (metodoPago != null && metodoPago != MetodoPago.MIXTO) {
                    statement.setString(index++, metodoPago.name());
                    statement.setString(index++, metodoPago.name());
                }
                statement.setString(index++, desde.toString());
                statement.setString(index, hasta.toString());
                try (var result = statement.executeQuery()) {
                    result.next();
                    ventas = result.getDouble("ventas");
                    costo = result.getDouble("costo");
                    ganancia = result.getDouble("ganancia");
                    tickets = result.getLong("tickets");
                }
            }

            double efectivo;
            double transferencia;
            double tarjeta;
            double fiado;
            double gananciaGlobal;
            try (var statement = connection.prepareStatement(globalSalesSql)) {
                int index = 1;
                for (int i = 0; i < 5; i++) {
                    statement.setString(index++, desde.toString());
                    statement.setString(index++, hasta.toString());
                }
                try (var result = statement.executeQuery()) {
                    result.next();
                    efectivo = result.getDouble("efectivo");
                    transferencia = result.getDouble("transferencia");
                    tarjeta = result.getDouble("tarjeta");
                    fiado = result.getDouble("fiado");
                    gananciaGlobal = result.getDouble("ganancia_global");
                }
            }

            double ingresos;
            double egresos;
            try (var statement = connection.prepareStatement(movementsSql)) {
                statement.setString(1, desde.toString());
                statement.setString(2, hasta.toString());
                try (var result = statement.executeQuery()) {
                    result.next();
                    ingresos = result.getDouble("ingresos");
                    egresos = result.getDouble("egresos");
                }
            }

            return new ReportePeriodoResumen(
                    desde,
                    hasta,
                    metodoPago,
                    ventas,
                    costo,
                    ganancia,
                    tickets,
                    tickets == 0 ? 0d : ventas / tickets,
                    efectivo,
                    transferencia,
                    tarjeta,
                    fiado,
                    ingresos,
                    egresos,
                    gananciaGlobal + ingresos - egresos
            );
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el resumen del período.", e);
        }
    }

    @Override
    public List<ReporteLineaTiempo> lineaTiempo(LocalDate desde, LocalDate hasta, MetodoPago metodoPago) {
        boolean mensual = ChronoUnit.DAYS.between(desde, hasta) > 62;
        String bucket = mensual
                ? "strftime('%Y-%m-01', fecha, 'localtime')"
                : "date(fecha, 'localtime')";

        String movimientos;
        boolean bindMetodo = false;
        if (metodoPago == null) {
            movimientos = """
                    SELECT v.fecha, v.total AS ventas, v.ganancia_total AS ganancia, 1 AS ticket
                    FROM venta v WHERE v.anulada = 0
                    UNION ALL
                    SELECT d.fecha, -d.total AS ventas, -d.ganancia_revertida AS ganancia, 0 AS ticket
                    FROM devolucion d
                    """;
        } else if (metodoPago == MetodoPago.MIXTO) {
            movimientos = """
                    SELECT v.fecha, v.total AS ventas, v.ganancia_total AS ganancia, 1 AS ticket
                    FROM venta v WHERE v.anulada = 0 AND v.metodo_pago = 'MIXTO'
                    UNION ALL
                    SELECT d.fecha, -d.total AS ventas, -d.ganancia_revertida AS ganancia, 0 AS ticket
                    FROM devolucion d WHERE d.metodo_pago = 'MIXTO'
                    """;
        } else {
            bindMetodo = true;
            movimientos = """
                    SELECT v.fecha, vp.monto AS ventas,
                           CASE WHEN v.total <= 0 THEN 0
                                ELSE v.ganancia_total * vp.monto / v.total END AS ganancia,
                           1 AS ticket
                    FROM venta v
                    JOIN venta_pago vp ON vp.venta_id = v.id
                    WHERE v.anulada = 0 AND vp.metodo_pago = ?
                    UNION ALL
                    SELECT d.fecha, -dp.monto AS ventas,
                           CASE WHEN d.total <= 0 THEN 0
                                ELSE -d.ganancia_revertida * dp.monto / d.total END AS ganancia,
                           0 AS ticket
                    FROM devolucion d
                    JOIN devolucion_pago dp ON dp.devolucion_id = d.id
                    WHERE dp.metodo_pago = ?
                    """;
        }

        String sql = "SELECT " + bucket + " AS periodo, "
                + "COALESCE(SUM(ventas), 0) AS ventas, "
                + "COALESCE(SUM(ganancia), 0) AS ganancia, "
                + "COALESCE(SUM(ticket), 0) AS tickets "
                + "FROM (" + movimientos + ") m "
                + "WHERE date(fecha, 'localtime') BETWEEN ? AND ? "
                + "GROUP BY periodo ORDER BY periodo";

        Map<LocalDate, ReporteLineaTiempo> encontrados = new LinkedHashMap<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (bindMetodo) {
                statement.setString(index++, metodoPago.name());
                statement.setString(index++, metodoPago.name());
            }
            statement.setString(index++, desde.toString());
            statement.setString(index, hasta.toString());
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    LocalDate periodo = LocalDate.parse(result.getString("periodo"));
                    encontrados.put(periodo, new ReporteLineaTiempo(
                            periodo,
                            result.getDouble("ventas"),
                            result.getDouble("ganancia"),
                            result.getLong("tickets")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar la línea de tiempo del reporte.", e);
        }

        List<ReporteLineaTiempo> timeline = new ArrayList<>();
        if (mensual) {
            LocalDate cursor = desde.withDayOfMonth(1);
            LocalDate fin = hasta.withDayOfMonth(1);
            while (!cursor.isAfter(fin)) {
                timeline.add(encontrados.getOrDefault(cursor, new ReporteLineaTiempo(cursor, 0, 0, 0)));
                cursor = cursor.plusMonths(1);
            }
        } else {
            LocalDate cursor = desde;
            while (!cursor.isAfter(hasta)) {
                timeline.add(encontrados.getOrDefault(cursor, new ReporteLineaTiempo(cursor, 0, 0, 0)));
                cursor = cursor.plusDays(1);
            }
        }
        return List.copyOf(timeline);
    }

    @Override
    public List<ProductoVendidoResumen> productosMasVendidos(
            LocalDate desde,
            LocalDate hasta,
            MetodoPago metodoPago,
            int limite
    ) {
        String filtroPago;
        if (metodoPago == null) {
            filtroPago = "";
        } else if (metodoPago == MetodoPago.MIXTO) {
            filtroPago = " AND m.metodo_pago = 'MIXTO' ";
        } else {
            filtroPago = """
                     AND (
                         (m.devolucion_id IS NULL AND EXISTS (
                             SELECT 1 FROM venta_pago vp
                             WHERE vp.venta_id = m.venta_id AND vp.metodo_pago = ?
                         ))
                         OR
                         (m.devolucion_id IS NOT NULL AND EXISTS (
                             SELECT 1 FROM devolucion_pago dp
                             WHERE dp.devolucion_id = m.devolucion_id AND dp.metodo_pago = ?
                         ))
                     )
                    """;
        }

        String sql = """
                WITH movimientos_producto AS (
                    SELECT v.id AS venta_id, NULL AS devolucion_id, v.fecha, v.metodo_pago, d.producto_id,
                           d.cantidad AS cantidad,
                           d.subtotal AS ventas,
                           d.subtotal - d.ganancia_linea AS costo,
                           d.ganancia_linea AS ganancia
                    FROM venta_detalle d
                    JOIN venta v ON v.id = d.venta_id
                    WHERE v.anulada = 0
                    UNION ALL
                    SELECT dv.venta_id, dv.id AS devolucion_id, dv.fecha, dv.metodo_pago, dd.producto_id,
                           -dd.cantidad AS cantidad,
                           -dd.subtotal AS ventas,
                           -(dd.subtotal - dd.ganancia_revertida) AS costo,
                           -dd.ganancia_revertida AS ganancia
                    FROM devolucion_detalle dd
                    JOIN devolucion dv ON dv.id = dd.devolucion_id
                )
                SELECT p.id AS producto_id, p.nombre, p.unidad_medida,
                       COALESCE(SUM(m.cantidad), 0) AS cantidad,
                       COALESCE(SUM(m.ventas), 0) AS ventas,
                       COALESCE(SUM(m.costo), 0) AS costo,
                       COALESCE(SUM(m.ganancia), 0) AS ganancia
                FROM movimientos_producto m
                JOIN producto p ON p.id = m.producto_id
                WHERE date(m.fecha, 'localtime') BETWEEN ? AND ?
                """ + filtroPago + """
                GROUP BY p.id, p.nombre, p.unidad_medida
                HAVING ABS(SUM(m.ventas)) > 0.000001 OR ABS(SUM(m.cantidad)) > 0.000001
                ORDER BY ventas DESC, cantidad DESC
                LIMIT ?
                """;

        List<ProductoVendidoResumen> productos = new ArrayList<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, desde.toString());
            statement.setString(index++, hasta.toString());
            if (metodoPago != null && metodoPago != MetodoPago.MIXTO) {
                statement.setString(index++, metodoPago.name());
                statement.setString(index++, metodoPago.name());
            }
            statement.setInt(index, Math.max(1, limite));
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    productos.add(new ProductoVendidoResumen(
                            result.getLong("producto_id"),
                            result.getString("nombre"),
                            UnidadMedida.valueOf(result.getString("unidad_medida")),
                            result.getDouble("cantidad"),
                            result.getDouble("ventas"),
                            result.getDouble("costo"),
                            result.getDouble("ganancia")
                    ));
                }
            }
            return List.copyOf(productos);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el ranking de productos.", e);
        }
    }

    @Override
    public List<VentaResumen> listarVentas(
            LocalDate desde,
            LocalDate hasta,
            MetodoPago metodoPago,
            int limite
    ) {
        String filtroPago = metodoPago == null
                ? ""
                : metodoPago == MetodoPago.MIXTO
                    ? " AND v.metodo_pago = ? "
                    : " AND EXISTS (SELECT 1 FROM venta_pago vp WHERE vp.venta_id = v.id AND vp.metodo_pago = ?) ";
        String sql = """
                SELECT v.id, v.nro_ticket, v.fecha, u.username, v.metodo_pago,
                       v.total, v.ganancia_total, v.anulada,
                       COALESCE((SELECT SUM(dv.total) FROM devolucion dv WHERE dv.venta_id = v.id), 0) AS devuelto
                FROM venta v
                JOIN usuario u ON u.id = v.usuario_id
                WHERE date(v.fecha, 'localtime') BETWEEN ? AND ?
                """ + filtroPago + """
                ORDER BY v.fecha DESC, v.id DESC
                LIMIT ?
                """;
        List<VentaResumen> ventas = new ArrayList<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, desde.toString());
            statement.setString(index++, hasta.toString());
            if (metodoPago != null) statement.setString(index++, metodoPago.name());
            statement.setInt(index, Math.max(1, limite));
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    ventas.add(new VentaResumen(
                            result.getLong("id"),
                            result.getLong("nro_ticket"),
                            parseDate(result.getString("fecha")),
                            result.getString("username"),
                            MetodoPago.valueOf(result.getString("metodo_pago")),
                            result.getDouble("total"),
                            result.getDouble("ganancia_total"),
                            result.getDouble("devuelto"),
                            result.getInt("anulada") != 0
                    ));
                }
            }
            return List.copyOf(ventas);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar el historial de ventas del período.", e);
        }
    }

    @Override
    public Optional<VentaDetalle> detalleVenta(long ventaId) {
        String saleSql = """
                SELECT v.id, v.nro_ticket, v.fecha, u.username, v.metodo_pago,
                       v.total, v.recibido, v.vuelto, v.ganancia_total, v.anulada,
                       c.nombre AS cliente
                FROM venta v
                JOIN usuario u ON u.id = v.usuario_id
                LEFT JOIN cliente c ON c.id = v.cliente_id
                WHERE v.id = ?
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(saleSql)) {
            statement.setLong(1, ventaId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                List<VentaDetalleItem> items = cargarItems(connection, ventaId);
                List<PagoVenta> pagos = cargarPagos(connection, ventaId);
                return Optional.of(new VentaDetalle(
                        result.getLong("id"),
                        result.getLong("nro_ticket"),
                        parseDate(result.getString("fecha")),
                        result.getString("username"),
                        MetodoPago.valueOf(result.getString("metodo_pago")),
                        result.getDouble("total"),
                        result.getDouble("recibido"),
                        result.getDouble("vuelto"),
                        result.getDouble("ganancia_total"),
                        result.getInt("anulada") != 0,
                        result.getString("cliente"),
                        items,
                        pagos
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar el detalle de la venta.", e);
        }
    }

    private List<PagoVenta> cargarPagos(Connection connection, long ventaId) throws SQLException {
        String sql = """
                SELECT metodo_pago, monto
                FROM venta_pago
                WHERE venta_id = ?
                ORDER BY CASE metodo_pago
                    WHEN 'EFECTIVO' THEN 1
                    WHEN 'TRANSFERENCIA' THEN 2
                    WHEN 'TARJETA' THEN 3
                    WHEN 'FIADO' THEN 4
                    ELSE 9 END
                """;
        List<PagoVenta> pagos = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ventaId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    pagos.add(new PagoVenta(
                            MetodoPago.valueOf(result.getString("metodo_pago")),
                            result.getDouble("monto")
                    ));
                }
            }
        }
        return List.copyOf(pagos);
    }

    private List<VentaDetalleItem> cargarItems(Connection connection, long ventaId) throws SQLException {
        String sql = """
                SELECT p.nombre, d.cantidad, d.precio_unitario, d.subtotal
                FROM venta_detalle d
                JOIN producto p ON p.id = d.producto_id
                WHERE d.venta_id = ?
                ORDER BY d.id
                """;
        List<VentaDetalleItem> items = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ventaId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    items.add(new VentaDetalleItem(
                            result.getString("nombre"),
                            result.getDouble("cantidad"),
                            result.getDouble("precio_unitario"),
                            result.getDouble("subtotal")
                    ));
                }
            }
        }
        return List.copyOf(items);
    }

    private LocalDateTime parseDate(String value) {
        LocalDateTime utc = LocalDateTime.parse(value, SQLITE_DATE);
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
