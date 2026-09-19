# SisTienda — Checklist de salida a producción

Este documento define las puertas mínimas para declarar una versión candidata lista para un comercio real.

## 1. Build y datos

- `./gradlew clean build --no-daemon` debe finalizar correctamente.
- El workflow `Windows Installer` debe generar el EXE de la misma versión que muestra SisTienda.
- La base debe permanecer fuera de la carpeta de instalación.
- Debe existir backup válido antes de pruebas destructivas o actualizaciones.
- Restaurar un backup debe cerrar la aplicación para evitar seguir operando con estado previo en memoria.

## 2. Matriz visual obligatoria

Validar como mínimo:

| Resolución / escala | Resultado esperado |
| --- | --- |
| 1366×768 · 100% | Sin acciones críticas cortadas |
| 1366×768 · 125% | Sin botones inaccesibles; usar scroll cuando haga falta |
| 1920×1080 · 100% | Layout completo |
| 1920×1080 · 125% | Layout completo |

En cada resolución revisar: Login, Catálogo, Caja/Ventas, Clientes & Fiado, Inventario, Reposición, Reportes, Compras, Configuración, Usuarios, Ticket y todos los diálogos.

## 3. Flujo comercial integral

1. Abrir caja.
2. Registrar compra y verificar costo/stock.
3. Vender UN en efectivo.
4. Vender KG con cantidad decimal.
5. Vender por transferencia y tarjeta.
6. Registrar fiado y luego un abono.
7. Hacer devolución parcial.
8. Validar devolución en efectivo cuando el neto del turno queda negativo.
9. Anular una venta mientras su caja original sigue abierta.
10. Registrar ingreso y egreso manual.
11. Cerrar caja y comprobar diferencia.
12. Revisar Dashboard, arqueo, inventario y stock.
13. Reiniciar y confirmar persistencia.

## 4. Reglas críticas

- Montos calculados se cuantizan a guaraní entero.
- Productos UN nunca aceptan cantidad decimal.
- Productos KG muestran coma decimal en interfaz/ticket.
- Una devolución se registra por el mismo medio de pago de la venta original.
- Ajustes manuales de stock deben quedar asociados al usuario.
- Una operación de caja no puede registrarse sobre la caja de otro usuario.
- Errores técnicos de SQLite/JDBC no deben mostrarse directamente al cliente.

## 5. Hardware

Antes de entregar una instalación como POS validado:

- scanner USB + Enter;
- ticket térmico 80 mm;
- ticket 58 mm si el cliente lo usa;
- etiqueta;
- impresión de ticket largo;
- comportamiento con impresora apagada/desconectada;
- códigos de peso/PLU cuando corresponda.

## 6. Windows

En una PC distinta al entorno de desarrollo:

1. instalar EXE;
2. abrir desde acceso directo sin Java/Gradle;
3. operar y cerrar;
4. reiniciar Windows y volver a abrir;
5. verificar `%LOCALAPPDATA%\SisTienda\sistienda.db`;
6. crear backup;
7. reinstalar/actualizar;
8. comprobar que base y backups permanecen;
9. verificar logs de soporte en `%LOCALAPPDATA%\SisTienda\logs`.

Una versión sólo pasa a producción cuando los puntos críticos de esta lista estén validados en la misma versión candidata.
