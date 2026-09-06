# SisTienda

SisTienda es una aplicación desktop offline para Windows orientada a la gestión de tiendas. Está construida con Java 21, JavaFX 21 y SQLite, utilizando Gradle como sistema de build y una arquitectura multi-módulo.

## Objetivo del MVP

SisTienda busca cubrir el circuito operativo esencial de una tienda sin depender de internet:

- acceso seguro del dueño;
- catálogo, categorías, costos y stock;
- caja y punto de venta;
- tickets e impresión;
- reportes de ventas y ganancia;
- configuración de empresa;
- backup y restauración;
- proveedores y compras;
- ingresos, egresos y control de efectivo;
- cierre y arqueo histórico de cajas;
- códigos de barras, etiquetas y preparación para hardware POS.

## Sprint 1 — Catálogo & Stock

- Alta de categorías.
- Alta, edición y desactivación segura de productos.
- Productos por unidad o kilogramo.
- Precio de venta y costo.
- Búsqueda y filtro por categoría.
- Indicadores de inventario.
- Entradas y salidas de stock con trazabilidad.
- Control de stock negativo.
- Protección para cambios de unidad y desactivación cuando existe stock.

El stock no se edita directamente en el producto: toda variación se registra mediante movimientos.

## Sprint 2 — Login, Caja & Ventas

- Primera configuración del usuario dueño.
- Login obligatorio.
- Contraseñas protegidas con PBKDF2-HMAC-SHA256 y salt aleatorio.
- Apertura y cierre de caja.
- Punto de venta compacto con productos y carrito en paralelo.
- Efectivo, tarjeta y transferencia.
- Vuelto automático.
- Resumen en vivo por método de pago.
- Ticket correlativo.
- Venta, detalle y salida de stock dentro de una transacción SQLite.

## Sprint 3 — Reportes & Control

- Reporte diario por fecha.
- Ventas, ganancia, tickets y ticket promedio.
- Desglose por medio de pago.
- Historial de ventas.
- Detalle de ticket.
- Ventas anuladas visibles para trazabilidad pero excluidas de métricas.
- Fechas SQLite convertidas a hora local para visualización.

## Sprint 4 — Configuración & Ticket

- Datos de empresa: nombre, RUC, dirección, teléfono y mensaje del ticket.
- Pantalla de configuración.
- Comprobante reutilizable.
- Apertura automática del ticket después de cobrar.
- Reimpresión desde Reportes.
- Impresión mediante la impresora del sistema.

## Sprint 5 — Backup & Seguridad

- Backup manual de la base SQLite.
- Backup automático de seguridad.
- Historial de backups.
- Restauración con validación de integridad.
- Backup de emergencia antes de restaurar.
- Pruebas reales de backup y restauración sobre SQLite temporal.

## Sprint 6 — Proveedores & Compras

- Alta, edición, búsqueda y desactivación de proveedores.
- Registro de compras con varios productos.
- Documento/factura, observación, cantidad y costo unitario.
- Entrada automática de stock.
- Actualización del último costo del producto.
- Historial y detalle de compras.
- Protección contra documento duplicado por proveedor.
- Compra, detalle, costo y stock confirmados o revertidos como una sola transacción.

## Sprint 7 — Gastos & Movimientos de Caja

- Ingresos y egresos dentro de la caja abierta.
- Categorías para alquiler, servicios, flete, compra menor, retiro, aportes, reintegros y otros.
- Trazabilidad por caja, usuario, fecha, concepto, monto y referencia.
- Control en vivo de ingresos y egresos.
- Efectivo esperado calculado como:

```text
fondo inicial
+ ventas en efectivo
+ ingresos
- egresos
= efectivo esperado
```

- Cierre con comparación entre esperado y contado.

## Sprint 8 — Cierre & Arqueo de Caja

- Historial de hasta las últimas 100 sesiones de caja.
- Búsqueda por usuario.
- Filtros por abiertas, cerradas y cajas con diferencia.
- Total vendido y cantidad de tickets por turno.
- Efectivo esperado y efectivo contado.
- Diferencia automática con identificación de caja exacta, sobrante o faltante.
- Detalle completo del arqueo.
- Ventas por efectivo, transferencia y tarjeta dentro del turno.
- Ingresos y egresos del turno.
- Listado de movimientos manuales.
- Observación de cierre.
- Consultas consolidadas sin duplicar importes al combinar ventas y movimientos.

## Sprint 9 — Códigos, Etiquetas & Hardware POS

- Código de barras por producto.
- Uso del código del fabricante o generación automática de un EAN-13 interno para productos por unidad.
- PLU de balanza para productos vendidos por kilogramo.
- Generación y validación de EAN-13 con dígito verificador.
- Formato variable de peso configurable:

```text
PP + PLU(5) + gramos(5) + dígito verificador
```

- Lectura de etiquetas de balanza desde Caja.
- Scanner USB compatible con modo teclado: el lector escribe el código y envía Enter.
- Escaneo repetido de productos por unidad acumula cantidad en el carrito.
- Escaneo de etiqueta de peso agrega al carrito el peso exacto codificado.
- Validación de stock sobre la cantidad acumulada del carrito.
- Búsqueda por nombre, categoría, código de barras o PLU.
- Vista previa e impresión de etiquetas desde Catálogo.
- Etiqueta de producto por unidad mediante Code 128.
- Etiqueta de producto pesable mediante EAN-13 variable.
- Configuración POS para prefijo de balanza, ticket 58/80 mm, ancho de etiqueta e impresión al cobrar.
- Ticket adaptado visualmente a 58 u 80 mm y reimpresión manual mediante el sistema de impresión de JavaFX.
- Migración compatible de bases creadas en sprints anteriores: se agregan columnas de código y PLU sin perder productos ni stock.
- Restricción de código de barras y PLU únicos por producto.
- ZXing se utiliza únicamente en `app-ui` para renderizar códigos; reglas, validación y decodificación permanecen en `app-core`.

### Compatibilidad con balanzas

Sprint 9 implementa un formato neutral que SisTienda puede generar y decodificar. Una balanza etiquetadora física deberá configurarse para emitir el mismo esquema o contar con un adaptador específico.

La sincronización automática del catálogo hacia una balanza por USB, RS-232, Ethernet o Wi-Fi **no se implementa de forma genérica**, porque depende del protocolo del fabricante. Se agregará el adaptador correspondiente cuando se seleccione la marca y modelo de balanza.

## Stack tecnológico

- Java 21
- JavaFX 21.0.4
- SQLite mediante `org.xerial:sqlite-jdbc`
- ZXing Core 3.5.3 para renderizado de códigos en la interfaz
- Gradle 8.10.2
- JUnit 5
- GitHub Actions

## Arquitectura

SisTienda está dividido en tres módulos:

```text
SisTienda
├── app-core
├── app-data
└── app-ui
```

### app-core

Dominio y reglas de negocio:

- modelos;
- contratos de repositorio;
- servicios;
- validaciones;
- excepciones de negocio.

No depende de JavaFX, JDBC ni SQLite.

### app-data

Infraestructura de persistencia:

- inicialización SQLite;
- conexiones;
- implementaciones `Sqlite*Repository`;
- acceso JDBC;
- recursos SQL.

Depende de `app-core`.

### app-ui

Aplicación JavaFX:

- `MainApp`;
- shell y navegación;
- vistas y diálogos;
- componentes visuales;
- CSS;
- composición de dependencias.

## Flujo de ejecución

```text
app-ui
   ↓
service / app-core
   ↓
repository contract / app-core
   ↓
repository implementation / app-data
   ↓
SQLite
```

La UI no ejecuta SQL directamente.

## Base de datos

La base de desarrollo se almacena en:

```text
~/.sistienda/dev/sistienda.db
```

El esquema inicial está en:

```text
app-data/src/main/resources/db/V1__init.sql
```

Entre otras, contiene:

- `empresa`
- `configuracion_pos`
- `usuario`
- `categoria_producto`
- `producto`
- `proveedor`
- `compra`
- `compra_detalle`
- `caja_sesion`
- `caja_movimiento`
- `venta`
- `venta_detalle`
- `mov_stock`
- `secuencia`

La base incluye controles para impedir stock negativo, mantener `stock_actual` actualizado y evitar códigos de barras o PLU duplicados.

## Ejecutar

### Windows

```bat
gradlew.bat :app-ui:run
```

### Linux/macOS

```bash
./gradlew :app-ui:run
```

## Compilar y verificar

### Windows

```bat
gradlew.bat clean build
```

### Linux/macOS

```bash
./gradlew clean build
```

GitHub Actions ejecuta:

```bash
./gradlew clean build --no-daemon
```

No se considera un cambio verificado hasta que build y tests terminan correctamente.

## Flujo Git

La rama base es:

```text
develop
```

Cada sprint o cambio importante usa una rama dedicada. No se hace merge de funcionalidades relevantes sin revisión explícita.

## Convención de commits

```text
feat: nueva funcionalidad
fix: corrección
refactor: reorganización interna
chore: mantenimiento
build: build o dependencias
test: pruebas
ci: integración continua
docs: documentación
```

## Reglas para agentes

Las reglas operativas se encuentran en:

```text
AGENTS.md
```

## Estado actual

Mergeado en `develop`:

- Sprint 0: arquitectura, documentación, tests base y CI.
- Sprint 1: catálogo y stock.
- Sprint 2: login, caja y ventas.

En ramas/PRs apilados pendientes de validación local:

- Sprint 3: reportes y control.
- Sprint 4: configuración y ticket.
- Sprint 5: backup y seguridad.
- Sprint 6: proveedores y compras.
- Sprint 7: gastos y movimientos de caja.
- Sprint 8: cierre y arqueo de caja.
- Sprint 9: códigos, etiquetas y hardware POS.

Siguientes frentes previstos:

- usuarios y roles;
- dashboard del dueño;
- reportes por rangos y exportaciones;
- anulación operativa de ventas con reversión de stock;
- adaptador de balanza específico cuando se defina el hardware;
- empaquetado, instalador y preparación de producción.

## Principio rector

SisTienda debe mantenerse simple, estable y entendible. La prioridad es construir una aplicación desktop confiable sin mezclar lógica de negocio, interfaz y persistencia.
