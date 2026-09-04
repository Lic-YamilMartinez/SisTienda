# AGENTS.md

## Propósito
Este archivo define las reglas de trabajo para cualquier agente o asistente que modifique SisTienda.

SisTienda es una aplicación desktop offline para Windows orientada a la gestión de tiendas. El stack actual es Java 21, JavaFX 21 y SQLite, organizado como proyecto Gradle multi-módulo.

## Arquitectura obligatoria
Mantener la separación por módulos:

- `app-core`: dominio, reglas de negocio, contratos de repositorio y servicios.
- `app-data`: persistencia, SQLite, inicialización de base de datos e implementaciones de repositorios.
- `app-ui`: JavaFX, navegación, controladores y componentes visuales.

Dependencias entre módulos:

- `app-core` no depende de `app-data` ni de `app-ui`.
- `app-data` depende de `app-core`.
- `app-ui` depende de `app-core` y `app-data` para composición/bootstrap.

Flujo de ejecución esperado:

`app-ui -> service/app-core -> repository contract/app-core -> repository implementation/app-data -> SQLite`

La UI no debe ejecutar SQL ni acceder directamente a JDBC.
`app-core` no debe depender de SQLite, JDBC, JavaFX ni clases de `app-data`.
`app-data` puede depender de `app-core`.
`app-ui` puede depender de `app-core` y `app-data` únicamente para composición/bootstrap.

## Convenciones de paquetes
### app-core
- `py.sistienda.core.model`
- `py.sistienda.core.repository`
- `py.sistienda.core.service`
- `py.sistienda.core.exception`
- `py.sistienda.core.validation`

### app-data
- `py.sistienda.data.database`
- `py.sistienda.data.repository`
- `py.sistienda.data.mapper`

### app-ui
- `py.sistienda.ui`
- `py.sistienda.ui.controller`
- `py.sistienda.ui.navigation`
- `py.sistienda.ui.component`

## Reglas de dominio y persistencia
- Los contratos de repositorio pertenecen a `app-core`.
- Las implementaciones SQLite deben llamarse con prefijo `Sqlite`, por ejemplo `SqliteCategoriaRepository`.
- Las conexiones SQLite deben obtenerse mediante `SqliteConnectionFactory`.
- Toda conexión SQLite debe tener `PRAGMA foreign_keys = ON`.
- No duplicar lógica de negocio entre UI, repositorios y triggers.
- Las reglas de negocio deben vivir preferentemente en servicios de `app-core`.
- No introducir dependencias externas sin necesidad clara.

## Base de datos
- El esquema actual se inicializa desde `app-data/src/main/resources/db/V1__init.sql`.
- No modificar ni reemplazar una migración existente de forma destructiva cuando ya pueda existir una base creada.
- Para cambios de esquema posteriores, preferir nuevas migraciones versionadas (`V2__...sql`, `V3__...sql`, etc.) cuando se implemente el mecanismo de migraciones.
- No subir archivos `.db` al repositorio.
- No insertar credenciales, claves ni secretos en el código o el repositorio.

## Dinero y cantidades
- No cambiar el modelo de tipos monetarios sin una decisión arquitectónica explícita.
- Actualmente existen columnas monetarias SQLite definidas como `REAL`; este punto está pendiente de revisión antes de ampliar el dominio.
- Las cantidades de stock pueden requerir decimales por soporte de productos por kilogramo.

## Java y estilo
- Usar Java 21.
- Mantener UTF-8.
- Favorecer código simple y legible sobre abstracciones innecesarias.
- Usar nombres de clases, métodos y variables descriptivos.
- Mantener responsabilidades pequeñas y claras.
- Evitar clases utilitarias globales cuando una dependencia pueda inyectarse explícitamente.
- No mezclar lógica de UI con acceso a datos.

## Tests y verificación
Antes de considerar terminada una tarea de código:

1. Ejecutar `./gradlew clean build` en Linux/macOS o `gradlew.bat clean build` en Windows.
2. Ejecutar los tests específicos del módulo cuando existan.
3. Corregir errores de compilación, tests o imports producidos por el cambio.
4. No declarar que el build pasó si no se ejecutó realmente.
5. Informar claramente cualquier verificación que no haya podido ejecutarse.

## Git y ramas
- Rama base de desarrollo: `develop`.
- No desarrollar funcionalidades importantes directamente sobre `develop`.
- Usar ramas con nombres descriptivos:
  - `feature/...`
  - `fix/...`
  - `chore/...`
  - `refactor/...`
- No hacer merge a `develop` sin revisión explícita.
- No usar `force push` salvo instrucción expresa y justificada.
- Mantener commits enfocados y descriptivos.

Convención sugerida de commits:
- `feat: ...`
- `fix: ...`
- `refactor: ...`
- `chore: ...`
- `test: ...`
- `docs: ...`

## Comportamiento esperado del agente
Antes de modificar:
1. Leer este `AGENTS.md`.
2. Inspeccionar los archivos relacionados con la tarea.
3. Respetar la arquitectura existente.
4. Evitar cambios fuera de alcance.

Durante la implementación:
1. Hacer el cambio mínimo necesario.
2. No borrar código existente sin entender su propósito.
3. No reescribir módulos completos si una modificación localizada resuelve el problema.
4. Mantener compatibilidad con el MVP desktop offline.

Al finalizar:
1. Resumir archivos modificados.
2. Explicar decisiones relevantes.
3. Informar resultados de build/tests.
4. Señalar riesgos o pendientes.
5. No hacer merge automáticamente a `develop` salvo instrucción explícita.

## Alcance actual del MVP
El objetivo funcional de SisTienda incluye:
- Login de dueño.
- Categorías y productos por unidad o kilogramo.
- Stock con entradas, salidas y control de stock negativo.
- Caja y sesiones de caja.
- Ventas y detalle de ventas.
- Ticket.
- Informe diario de ventas, movimientos y ganancia.

No ampliar el alcance del producto sin una instrucción explícita.
