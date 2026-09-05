# SisTienda

SisTienda es una aplicación desktop offline para Windows orientada a la gestión de tiendas. Está construida con Java 21, JavaFX 21 y SQLite, utilizando Gradle como sistema de build y una arquitectura multi-módulo.

## Objetivo del MVP
El MVP de SisTienda contempla:

- Login de dueño.
- Categorías y productos por unidad o kilogramo.
- Gestión de stock con entradas, salidas y control de stock negativo.
- Caja y sesiones de caja.
- Ventas y detalle de ventas.
- Ticket.
- Informe diario de ventas, movimientos y ganancia.

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

## Estructura actual relevante

```text
app-core/src/main/java/py/sistienda/core
├── exception
├── model
├── repository
└── service

app-core/src/test/java/py/sistienda/core
└── service

app-data/src/main/java/py/sistienda/data
├── database
└── repository

app-data/src/test/java/py/sistienda/data
├── database
└── repository

app-data/src/main/resources/db
└── V1__init.sql

app-ui/src/main/java/py/sistienda/ui
├── MainApp.java
├── MainShell.java
└── catalogo
    ├── CatalogoView.java
    ├── ProductoDialog.java
    └── StockDialog.java

app-ui/src/main/resources/styles
└── app.css
```

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

Los tests de `app-data` incluyen cobertura de integración sobre una base SQLite temporal para verificar inicialización, persistencia de categorías/productos, movimientos de stock y el control de stock negativo.

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
feature/ventas
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

Pendiente para siguientes sprints:
- Login y usuarios.
- Caja y sesiones de caja.
- Ventas y detalle de ventas.
- Ticket.
- Informe diario y ganancia.
- Ampliar cobertura de tests junto con cada funcionalidad.

## Principio rector
SisTienda debe mantenerse simple, estable y entendible. La prioridad es construir un MVP desktop confiable sin mezclar lógica de negocio, interfaz y persistencia.
