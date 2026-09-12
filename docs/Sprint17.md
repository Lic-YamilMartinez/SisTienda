# Sprint 17 — Reposición & onboarding

## Objetivo

Completar el control de inventario con alertas accionables y reducir drásticamente el tiempo necesario para poner en marcha un comercio nuevo.

## Reposición

Cada producto puede tener:

- **Stock mínimo:** nivel en el que SisTienda avisa que conviene reponer.
- **Stock ideal:** cantidad objetivo que se desea volver a tener.

La alerta queda desactivada cuando el stock ideal es 0. Cuando el stock actual llega al mínimo o queda por debajo, SisTienda calcula:

`cantidad sugerida = stock ideal - stock actual`

La pantalla **Reposición** muestra los productos pendientes, prioriza los que están sin stock, estima la inversión al costo y permite copiar la lista para pegarla en WhatsApp, correo o notas.

## Importación masiva

Desde **Catálogo & Stock → Importar Excel / CSV** se puede:

1. descargar la plantilla oficial de SisTienda;
2. seleccionar `.xlsx`, `.xls`, `.csv` o `.txt`;
3. revisar una vista previa;
4. detectar filas válidas y filas con observaciones;
5. importar únicamente las filas válidas.

Columnas reconocidas:

- Nombre *(obligatorio)*
- Categoria
- Unidad *(UN o KG, obligatorio)*
- Costo
- PrecioVenta *(obligatorio)*
- StockInicial
- StockMinimo
- StockIdeal
- CodigoBarras
- PLUBalanza

Las categorías inexistentes se crean automáticamente. Los códigos de barras y PLU se validan contra el archivo y contra la base antes de guardar. Si UN no trae código, SisTienda genera un código interno; si KG no trae PLU, asigna uno automáticamente cuando es posible.

El stock inicial se registra mediante un movimiento de entrada auditado con el usuario que ejecutó la importación. La persistencia es transaccional: ante un error de base, no queda una importación parcial.

## Permisos

La importación requiere simultáneamente permisos de gestión de catálogo y stock, por lo que queda disponible para Dueño y Administrador con la matriz actual de roles.
