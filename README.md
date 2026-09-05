# SisTienda

SisTienda es una aplicación desktop offline para Windows orientada a la gestión de tiendas. Está construida con Java 21, JavaFX 21 y SQLite, utilizando Gradle como sistema de build y una arquitectura multi-módulo.

## Objetivo del MVP

El MVP contempla:

- Login de dueño.
- Categorías y productos por unidad o kilogramo.
- Gestión de stock con entradas, salidas y control de stock negativo.
- Caja y sesiones de caja.
- Ventas y detalle de ventas.
- Ticket correlativo.
- Informe diario de ventas, movimientos y ganancia.

## Sprint 1 — Catálogo & Stock

Incluye:

- Alta de categorías.
- Alta, edición y desactivación segura de productos.
- Productos vendidos por unidad o kilogramo.
- Precio de venta y costo.
- Búsqueda y filtro por categoría.
- Indicadores de inventario.
- Entradas y salidas de stock con trazabilidad.
- Control de stock negativo en negocio y SQLite.
- Protección para no cambiar UN/KG ni desactivar productos mientras exista stock.
- Interfaz JavaFX con navegación lateral, tarjetas, tabla y diálogos guiados.

El stock no se edita directamente en el producto: toda variación se registra como movimiento.

## Sprint 2 — Login, Caja & Ventas

Incluye:

- Primera configuración del usuario dueño.
- Login obligatorio antes de acceder al sistema.
- Contraseñas protegidas con PBKDF2-HMAC-SHA256, salt aleatorio e iteraciones.
- Sesión del usuario visible en la aplicación.
- Apertura de caja con fondo inicial y notas.
- Cierre de caja con monto contado y notas.
- Navegación habilitada entre Catálogo y Caja.
- Punto de venta con búsqueda de productos.
- Carrito con cantidades para productos por unidad o kilogramo.
- Métodos de pago: efectivo, tarjeta y transferencia.
- Cálculo de total y vuelto.
- Ticket correlativo.
- Registro de venta y detalle.
- Salida automática de stock asociada a la venta.
- Venta, detalle, ticket y movimientos de stock guardados en una única transacción SQLite.
- Validación de stock en negocio y en base de datos.

El sistema no permite registrar ventas sin una caja abierta.

## Stack tecnológico

- Java 21
- JavaFX 21.0.4
- SQLite mediante `org.xerial:sqlite-jdbc`
- Gradle 8.10.2
- JUnit 5
- GitHub Actions

## Arquitectura

```text
SisTienda
├── app-core
├── app-data
└── app-ui
```

### app-core

Contiene dominio y reglas de negocio:

- modelos;
- contratos de repositorio;
- servicios;
- validaciones;
- excepciones;
- seguridad basada únicamente en APIs estándar de Java.

No depende de JavaFX, JDBC ni SQLite.

### app-data

Contiene persistencia:

- inicialización SQLite;
- fábrica de conexiones;
- repositorios JDBC;
- transacciones;
- recursos SQL.

Depende de `app-core`.

### app-ui

Contiene JavaFX:

- `MainApp`;
- shell y navegación;
- login;
- catálogo;
- caja;
- punto de venta;
- diálogos y estilos CSS.

## Dependencias entre módulos

```text
app-core   <-   app-data
   ^              ^
   └----------- app-ui
```

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

El esquema se encuentra en:

```text
app-data/src/main/resources/db/V1__init.sql
```

Tablas principales:

- `empresa`
- `usuario`
- `categoria_producto`
- `producto`
- `caja_sesion`
- `venta`
- `venta_detalle`
- `mov_stock`
- `secuencia`

SQLite incluye triggers para impedir stock negativo y mantener `stock_actual` sincronizado.

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

Tests:

```bash
./gradlew test
```

La suite incluye tests unitarios de servicios y pruebas de integración con SQLite temporal para catálogo, usuarios, caja y ventas.

GitHub Actions ejecuta:

```bash
./gradlew clean build --no-daemon
```

## Flujo de trabajo Git

Rama base:

```text
develop
```

Ejemplos de ramas:

```text
feature/sprint-1-catalog-stock
feature/sprint-2-login-caja-ventas
fix/stock-negativo
```

No se desarrolla funcionalidad importante directamente sobre `develop` y no se hace merge sin revisión explícita.

## Convención de commits

```text
feat: nueva funcionalidad
fix: corrección
refactor: reorganización interna
chore: mantenimiento
build: cambios de build o dependencias
test: pruebas
ci: integración continua
docs: documentación
```

## Reglas para agentes y Codex

Las reglas operativas se encuentran en:

```text
AGENTS.md
```

Cualquier agente debe leer ese archivo antes de modificar código.

## Estado actual

Completado:

- Sprint 0: arquitectura, documentación, tests base y CI.
- Sprint 1: catálogo, categorías y stock.
- Sprint 2: login, caja y punto de venta en fase de validación final antes de merge.

Siguientes frentes del MVP:

- ticket imprimible;
- informe diario;
- reportes de ventas y ganancia;
- configuración de empresa;
- ampliación de cobertura de tests.

## Principio rector

SisTienda debe mantenerse simple, estable y entendible. La prioridad es un MVP desktop confiable sin mezclar lógica de negocio, interfaz y persistencia.
