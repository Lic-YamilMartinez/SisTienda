# SisTienda

SisTienda es una aplicación desktop offline para Windows orientada a la gestión de tiendas. Está construida con Java 21, JavaFX 21 y SQLite, utilizando Gradle como sistema de build y una arquitectura multi-módulo.

## Objetivo del MVP
El MVP de SisTienda contempla:

- Login de dueño.
- Categorías y productos por unidad o kilogramo.
- Gestión de stock con entradas, salidas y control de stock negativo.
- Caja y sesiones de caja.
- Ventas y detalle de ventas.
- Ticket correlativo y consulta de ticket.
- Informe diario de ventas y ganancia.
- Historial diario de ventas.

## Sprint 1 — Catálogo & Stock

El módulo de catálogo e inventario incluye:

- Alta de categorías.
- Alta, edición y desactivación segura de productos.
- Productos vendidos por unidad o kilogramo.
- Precio de venta y costo.
- Búsqueda y filtro por categoría.
- Indicadores de productos activos, faltantes, categorías y valor de inventario al costo.
- Entradas y salidas de stock con motivo, referencia y observación.
- Control de stock negativo tanto en negocio como en SQLite.
- Protección para no cambiar la unidad de venta mientras exista stock.
- Protección para no desactivar productos que todavía tengan existencias.
- Interfaz JavaFX con navegación lateral, tarjetas de resumen, tabla de catálogo y diálogos guiados.

El stock no se edita directamente en el producto: toda variación se registra como un movimiento para conservar trazabilidad.

## Sprint 2 — Login, Caja & Ventas

El flujo operativo del MVP incorpora:

- Primera configuración del usuario dueño.
- Login obligatorio antes de acceder al sistema.
- Contraseñas protegidas con PBKDF2-HMAC-SHA256 y salt aleatorio.
- Apertura de caja con fondo inicial.
- Cierre de caja con monto contado.
- Punto de venta con productos y carrito en paralelo.
- Checkout compacto con método de pago, recibido, total, vuelto y cobro siempre visibles.
- Barra compacta de estado de caja y cierre mediante diálogo, sin desplazamiento vertical durante cada venta.
- Métodos de pago en efectivo, tarjeta y transferencia.
- Resumen en vivo de la caja abierta por método de pago.
- Ticket correlativo.
- Registro atómico de venta, detalle y movimientos de stock.
- Refresco del catálogo y caja al navegar entre módulos.

## Sprint 3 — Reportes & Control

El módulo de reportes incorpora:

- Reporte diario por fecha seleccionable.
- Ventas totales del día.
- Ganancia total del día.
- Cantidad de tickets.
- Ticket promedio.
- Desglose por efectivo, transferencia y tarjeta.
- Historial diario de ventas con hora, ticket, usuario, pago, total, ganancia y estado.
- Apertura del detalle de cada ticket desde la tabla.
- Detalle de productos, cantidades, precio unitario y subtotal.
- Información de pago, recibido, vuelto, total y ganancia dentro del ticket.
- Exclusión de ventas anuladas de las métricas del día, manteniéndolas visibles en el historial.
- Conversión de timestamps UTC de SQLite a la zona horaria local para visualización.

## Stack tecnológico

- Java 21
- JavaFX 21.0.4
- SQLite mediante `org.xerial:sqlite-jdbc`
- Gradle 8.10.2
- JUnit 5 para pruebas
- GitHub Actions para CI

## Arquitectura
SisTienda está dividido en tres módulos:

```text
SisTienda
├── app-core
├── app-data
└── app-ui
```

### app-core
Contiene el dominio y las reglas de negocio.

Responsabilidades principales:
- Modelos de dominio.
- Contratos de repositorio.
- Servicios.
- Validaciones.
- Excepciones de negocio.

Este módulo no debe depender de JavaFX, JDBC ni SQLite.

### app-data
Contiene la infraestructura de persistencia.

Responsabilidades principales:
- Inicialización de SQLite.
- Conexiones a la base de datos.
- Implementaciones concretas de repositorios.
- Acceso JDBC.
- Recursos SQL.

Este módulo depende de `app-core`.

### app-ui
Contiene la aplicación JavaFX.

Responsabilidades principales:
- `MainApp`.
- Shell y navegación.
- Vistas y diálogos.
- Componentes visuales.
- CSS.

Este módulo depende de `app-core` y `app-data` para el bootstrap de la aplicación.

## Dependencias entre módulos

```text
app-core   <-   app-data
   ^              ^
   └----------- app-ui
```

- `app-core` no depende de `app-data` ni de `app-ui`.
- `app-data` depende de `app-core`.
- `app-ui` depende de `app-core` y `app-data` para composición/bootstrap.

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

La UI no debe ejecutar SQL directamente. Los contratos de repositorio pertenecen a `app-core` y sus implementaciones SQLite pertenecen a `app-data`.

## Base de datos
La base de desarrollo se almacena dentro del directorio del usuario en:

```text
~/.sistienda/dev/sistienda.db
```

El esquema inicial se encuentra en:

```text
app-data/src/main/resources/db/V1__init.sql
```

Actualmente contempla, entre otras, las siguientes tablas:
- `empresa`
- `usuario`
- `categoria_producto`
- `producto`
- `caja_sesion`
- `venta`
- `venta_detalle`
- `mov_stock`
- `secuencia`

La base incluye triggers para evitar salidas de stock superiores a la existencia disponible y para mantener `stock_actual` actualizado.

## Ejecutar el proyecto

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

La suite de tests se ejecuta como parte de `build` y también puede ejecutarse con:

```bash
./gradlew test
```

GitHub Actions ejecuta `./gradlew clean build --no-daemon` para validar pushes de ramas de trabajo y pull requests hacia `develop`.

No se debe considerar un cambio verificado hasta ejecutar el build correctamente.

## Flujo de trabajo Git
La rama base de desarrollo es:

```text
develop
```

Para cambios nuevos se utilizan ramas dedicadas, por ejemplo:

```text
feature/sprint-1-catalog-stock
feature/sprint-2-login-caja-ventas
feature/sprint-3-reportes-control
fix/stock-negativo
```

No se deben desarrollar funcionalidades importantes directamente sobre `develop` ni hacer merge sin revisión explícita.

## Convención de commits

```text
feat: nueva funcionalidad
fix: corrección
refactor: reorganización interna
chore: mantenimiento
build: cambios de build o dependencias
test: pruebas
ci: automatización de integración continua
docs: documentación
```

## Reglas para agentes y Codex
Las reglas operativas del proyecto se encuentran en:

```text
AGENTS.md
```

Cualquier agente que trabaje sobre el repositorio debe leer ese archivo antes de modificar código.

## Estado actual

Completado:
- Sprint 0: arquitectura, documentación, tests base y CI.
- Sprint 1: catálogo de productos, categorías y movimientos de stock.
- Sprint 2: login, caja, ventas y experiencia compacta de punto de venta.

En validación:
- Sprint 3: reporte diario, ganancia, historial y detalle de tickets.

Pendiente para siguientes sprints:
- Ticket imprimible / impresión física.
- Configuración de empresa.
- Anulación operativa de ventas con reversión de stock.
- Reportes por rangos de fecha y análisis ampliados.
- Ampliar cobertura de tests junto con cada funcionalidad.

## Principio rector
SisTienda debe mantenerse simple, estable y entendible. La prioridad es construir un MVP desktop confiable sin mezclar lógica de negocio, interfaz y persistencia.
