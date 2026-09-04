package py.sistienda.data.repository;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.TipoMovimientoStock;
import py.sistienda.core.repository.MovimientoStockRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.PreparedStatement;
import java.util.Objects;

public final class SqliteMovimientoStockRepository implements MovimientoStockRepository {

    private final SqliteConnectionFactory connectionFactory;

    public SqliteMovimientoStockRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public void register(long productoId, TipoMovimientoStock tipo, String motivo, double cantidad,
                         String referencia, String observacion) {
        String sql = """
                INSERT INTO mov_stock
                    (producto_id, tipo, motivo, cantidad, referencia, observacion)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productoId);
            statement.setString(2, tipo.name());
            statement.setString(3, motivo);
            statement.setDouble(4, cantidad);
            statement.setString(5, referencia);
            statement.setString(6, observacion);
            statement.executeUpdate();
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("stock insuficiente")) {
                throw new ValidationException("No hay stock suficiente para realizar esa salida.");
            }
            throw new RuntimeException("No se pudo registrar el movimiento de stock.", e);
        }
    }
}
