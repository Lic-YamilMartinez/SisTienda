# SisTienda 0.9.0 — Piloto Windows

## Objetivo

Esta versión prepara SisTienda para instalarse en una PC Windows de un comercio piloto sin depender de Gradle, IntelliJ ni un JDK instalado por el cliente.

## Datos del negocio

Los datos nunca se guardan dentro de la carpeta donde Windows instala el programa.

Ubicación por defecto en Windows:

```text
%LOCALAPPDATA%\SisTienda\
  sistienda.db
  backups\
```

Esto permite actualizar o reinstalar SisTienda sin reemplazar la base del comercio.

Para soporte o pruebas se puede definir otra carpeta antes de iniciar la app:

```text
-Dsistienda.data.dir=C:\Ruta\Del\Cliente
```

También se admite la variable de entorno:

```text
SISTIENDA_DATA_DIR
```

## Migración desde los sprints anteriores

Al iniciar por primera vez, si la nueva ubicación todavía no tiene una base, SisTienda busca la ubicación histórica:

```text
~/.sistienda/dev/sistienda.db
```

y copia de forma no destructiva la base y los backups disponibles. Nunca reemplaza una base existente en la ubicación nueva.

## Generar instalador en Windows

Requisitos de la PC que construye el instalador:

- Windows 10/11;
- JDK 21 con `jpackage`;
- WiX Toolset 3.x para generar EXE.

Desde PowerShell en la raíz del proyecto:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-windows-installer.ps1
```

El script primero ejecuta todas las pruebas. Si alguna falla, no genera instalador.

Salida esperada:

```text
app-ui\build\installer\SisTienda-0.9.0.exe
```

El nombre exacto puede variar ligeramente según `jpackage`.

## Instalación piloto

El instalador es por usuario, crea acceso directo y entrada de menú Inicio. No necesita escribir la base en `Program Files`.

Antes de entregar a un comercio:

1. crear un backup manual de la base de prueba;
2. instalar SisTienda;
3. iniciar y configurar nombre/logo del comercio;
4. crear el usuario Dueño;
5. cargar o importar el catálogo inicial;
6. probar una venta en efectivo y otra transferencia;
7. probar devolución/anulación;
8. cerrar caja y revisar Dashboard;
9. crear y restaurar un backup de prueba;
10. recién después iniciar el período piloto con operaciones reales.

## GitHub Actions

La rama de Sprint 14 también genera automáticamente un artefacto Windows mediante el workflow `Windows Installer`. El EXE resultante queda como artefacto de GitHub Actions durante 14 días.

## Regla de actualización

Una nueva versión del programa puede reemplazar la instalación, pero no debe borrar ni reemplazar:

```text
%LOCALAPPDATA%\SisTienda\sistienda.db
%LOCALAPPDATA%\SisTienda\backups\
```

Antes de cualquier actualización importante debe existir al menos un backup válido.
