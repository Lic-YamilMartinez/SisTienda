PRAGMA foreign_keys = ON;

-- EMPRESA
CREATE TABLE IF NOT EXISTS empresa (
  id               INTEGER PRIMARY KEY CHECK (id = 1),
  nombre           TEXT NOT NULL,
  ruc              TEXT,
  direccion        TEXT,
  telefono         TEXT,
  mensaje_ticket   TEXT
);
INSERT OR IGNORE INTO empresa (id, nombre) VALUES (1, 'Mi Tienda');

-- USUARIO (MVP: dueño)
CREATE TABLE IF NOT EXISTS usuario (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  username         TEXT NOT NULL UNIQUE,
  password_hash    TEXT NOT NULL,
  rol              TEXT NOT NULL DEFAULT 'DUENIO',
  activo           INTEGER NOT NULL DEFAULT 1,
  creado_en        TEXT NOT NULL DEFAULT (datetime('now'))
);

-- CATEGORIAS
CREATE TABLE IF NOT EXISTS categoria_producto (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre           TEXT NOT NULL UNIQUE,
  activo           INTEGER NOT NULL DEFAULT 1
);

INSERT OR IGNORE INTO categoria_producto (nombre) VALUES
('Limpieza'), ('Cosméticos'), ('Granel'), ('Alimentos'), ('Bebidas');

-- PRODUCTOS
CREATE TABLE IF NOT EXISTS producto (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre           TEXT NOT NULL,
  categoria_id     INTEGER,
  unidad_medida    TEXT NOT NULL CHECK (unidad_medida IN ('UN','KG')),
  precio_venta     REAL NOT NULL CHECK (precio_venta >= 0),
  costo            REAL NOT NULL DEFAULT 0 CHECK (costo >= 0),
  stock_actual     REAL NOT NULL DEFAULT 0,
  activo           INTEGER NOT NULL DEFAULT 1,
  creado_en        TEXT NOT NULL DEFAULT (datetime('now')),
  actualizado_en   TEXT,
  FOREIGN KEY (categoria_id) REFERENCES categoria_producto(id)
);

CREATE INDEX IF NOT EXISTS idx_producto_nombre ON producto(nombre);

-- PROVEEDORES
CREATE TABLE IF NOT EXISTS proveedor (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre           TEXT NOT NULL,
  ruc              TEXT,
  telefono         TEXT,
  email            TEXT,
  direccion        TEXT,
  activo           INTEGER NOT NULL DEFAULT 1,
  creado_en        TEXT NOT NULL DEFAULT (datetime('now')),
  actualizado_en   TEXT
);

CREATE INDEX IF NOT EXISTS idx_proveedor_nombre ON proveedor(nombre COLLATE NOCASE);

-- CAJA SESION
CREATE TABLE IF NOT EXISTS caja_sesion (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  usuario_id       INTEGER NOT NULL,
  fecha_apertura   TEXT NOT NULL DEFAULT (datetime('now')),
  fecha_cierre     TEXT,
  monto_apertura   REAL NOT NULL DEFAULT 0 CHECK (monto_apertura >= 0),
  monto_cierre     REAL,
  estado           TEXT NOT NULL DEFAULT 'ABIERTA' CHECK (estado IN ('ABIERTA','CERRADA')),
  notas            TEXT,
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

-- COMPRA
CREATE TABLE IF NOT EXISTS compra (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  proveedor_id     INTEGER NOT NULL,
  usuario_id       INTEGER NOT NULL,
  fecha            TEXT NOT NULL DEFAULT (datetime('now')),
  nro_documento    TEXT,
  total            REAL NOT NULL CHECK (total >= 0),
  observacion      TEXT,
  FOREIGN KEY (proveedor_id) REFERENCES proveedor(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_compra_proveedor_documento
ON compra(proveedor_id, nro_documento)
WHERE nro_documento IS NOT NULL AND trim(nro_documento) <> '';

CREATE INDEX IF NOT EXISTS idx_compra_fecha ON compra(fecha DESC);

-- COMPRA DETALLE
CREATE TABLE IF NOT EXISTS compra_detalle (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  compra_id        INTEGER NOT NULL,
  producto_id      INTEGER NOT NULL,
  cantidad         REAL NOT NULL CHECK (cantidad > 0),
  costo_unitario   REAL NOT NULL CHECK (costo_unitario >= 0),
  subtotal         REAL NOT NULL CHECK (subtotal >= 0),
  FOREIGN KEY (compra_id) REFERENCES compra(id) ON DELETE CASCADE,
  FOREIGN KEY (producto_id) REFERENCES producto(id)
);

-- VENTA
CREATE TABLE IF NOT EXISTS venta (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  caja_sesion_id   INTEGER,
  usuario_id       INTEGER NOT NULL,
  fecha            TEXT NOT NULL DEFAULT (datetime('now')),
  total            REAL NOT NULL CHECK (total >= 0),
  total_lista      REAL NOT NULL DEFAULT 0,
  ganancia_total   REAL NOT NULL DEFAULT 0,
  metodo_pago      TEXT NOT NULL DEFAULT 'EFECTIVO',
  recibido         REAL DEFAULT 0 CHECK (recibido >= 0),
  vuelto           REAL DEFAULT 0 CHECK (vuelto >= 0),
  nro_ticket       INTEGER,
  anulada          INTEGER NOT NULL DEFAULT 0,
  motivo_anulacion TEXT,
  FOREIGN KEY (caja_sesion_id) REFERENCES caja_sesion(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

-- VENTA DETALLE
CREATE TABLE IF NOT EXISTS venta_detalle (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  venta_id         INTEGER NOT NULL,
  producto_id      INTEGER NOT NULL,
  cantidad         REAL NOT NULL CHECK (cantidad > 0),
  precio_unitario  REAL NOT NULL CHECK (precio_unitario >= 0),
  precio_lista     REAL NOT NULL DEFAULT 0,
  costo_unitario   REAL NOT NULL DEFAULT 0,
  subtotal         REAL NOT NULL CHECK (subtotal >= 0),
  ganancia_linea   REAL NOT NULL DEFAULT 0,
  FOREIGN KEY (venta_id) REFERENCES venta(id) ON DELETE CASCADE,
  FOREIGN KEY (producto_id) REFERENCES producto(id)
);

-- MOVIMIENTOS DE STOCK
CREATE TABLE IF NOT EXISTS mov_stock (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  producto_id      INTEGER NOT NULL,
  fecha            TEXT NOT NULL DEFAULT (datetime('now')),
  tipo             TEXT NOT NULL CHECK (tipo IN ('ENTRADA','SALIDA')),
  motivo           TEXT NOT NULL,
  cantidad         REAL NOT NULL CHECK (cantidad > 0),
  referencia       TEXT,
  usuario_id       INTEGER,
  observacion      TEXT,
  FOREIGN KEY (producto_id) REFERENCES producto(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE TRIGGER IF NOT EXISTS trg_mov_stock_no_negativo
BEFORE INSERT ON mov_stock
WHEN NEW.tipo = 'SALIDA'
BEGIN
  SELECT RAISE(ABORT, 'Stock insuficiente para realizar la venta')
  WHERE (SELECT stock_actual FROM producto WHERE id = NEW.producto_id) < NEW.cantidad;
END;

-- ACTUALIZA STOCK
CREATE TRIGGER IF NOT EXISTS trg_mov_stock_insert
AFTER INSERT ON mov_stock
BEGIN
  UPDATE producto
  SET stock_actual = CASE
      WHEN NEW.tipo = 'ENTRADA' THEN stock_actual + NEW.cantidad
      WHEN NEW.tipo = 'SALIDA'  THEN stock_actual - NEW.cantidad
    END,
    actualizado_en = datetime('now')
  WHERE id = NEW.producto_id;
END;

-- SECUENCIA DE TICKET
CREATE TABLE IF NOT EXISTS secuencia (
  clave TEXT PRIMARY KEY,
  valor INTEGER NOT NULL
);

INSERT OR IGNORE INTO secuencia (clave, valor) VALUES ('TICKET', 0);
