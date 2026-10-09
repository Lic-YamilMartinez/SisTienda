# Migración inicial de clientes y deudas

Este procedimiento se usa cuando un comercio comienza a trabajar con SisTienda y ya posee clientes con deudas anteriores.

## Regla contable

Una deuda anterior a SisTienda **no se registra como una venta nueva**.

Cargar un saldo inicial:

- aumenta la cuenta por cobrar del cliente;
- no modifica stock;
- no aumenta ventas;
- no aumenta ganancia;
- no genera ticket;
- no mueve dinero en caja.

Cuando el cliente paga esa deuda posteriormente:

- disminuye su cuenta por cobrar;
- si paga en efectivo, aumenta el efectivo esperado de la caja;
- transferencia y tarjeta quedan registradas por su medio;
- el cobro no se vuelve a contar como venta;
- el cobro no genera una nueva ganancia.

## Carga manual

En **Clientes & Fiado**:

1. Crear el cliente si todavía no existe.
2. Elegir **+ Saldo inicial**.
3. Seleccionar el cliente.
4. Cargar:
   - saldo inicial;
   - fecha de referencia;
   - referencia, por ejemplo `Cuaderno anterior`;
   - observación opcional.
5. Confirmar.

Cada cliente admite un único saldo inicial migrado. Esto evita duplicar accidentalmente la cartera histórica.

## Migración masiva

En **Clientes & Fiado** elegir **Migrar Excel / CSV**.

Se puede descargar una plantilla desde la misma ventana.

Columnas:

| Campo | Obligatorio | Uso |
| --- | --- | --- |
| Nombre | Sí | Nombre del cliente |
| Documento | No | CI/RUC. Si ya existe, vincula el saldo al cliente existente |
| Telefono | No | Ayuda a reconocer clientes sin documento |
| Direccion | No | Domicilio |
| SaldoInicial | Sí | Deuda anterior a SisTienda |
| FechaReferencia | No | Vacío = fecha de migración |
| Referencia | No | Ej. Cuaderno anterior |
| Observacion | No | Información adicional |

La vista previa distingue:

- cliente nuevo;
- cliente ya existente;
- filas válidas;
- filas con observaciones;
- total de cartera que se migrará.

Las filas inválidas se omiten hasta ser corregidas.

## Separación de cartera

Clientes & Fiado muestra:

- **Por cobrar:** deuda total actual;
- **Deuda anterior:** parte pendiente proveniente de la migración;
- **Fiado SisTienda:** deuda generada por ventas registradas en el sistema;
- **Clientes con deuda:** cantidad de clientes con saldo pendiente.

Los cobros se imputan primero a la deuda histórica y luego al fiado generado por SisTienda. Esto permite medir cuánto de la cartera vieja ya se recuperó.

## Validación antes del piloto

Antes de comenzar a vender con datos reales:

1. Hacer backup de la base.
2. Comparar el total del cuaderno anterior contra **Deuda anterior** en SisTienda.
3. Revisar al menos 5 clientes individualmente.
4. Verificar documento/teléfono y saldo.
5. Registrar un abono de prueba.
6. Confirmar:
   - baja del saldo;
   - aumento del efectivo esperado si fue efectivo;
   - ventas y ganancia sin cambios.
7. Revertir el entorno de prueba o usar una copia limpia antes de comenzar el piloto real.

## Ejemplo

Cartera previa:

- María: Gs. 120.000
- Juan: Gs. 75.000
- Rosa: Gs. 300.000

Total migrado:

**Gs. 495.000**

Después María paga Gs. 50.000 en efectivo:

- deuda de María: Gs. 70.000;
- cartera migrada pendiente: Gs. 445.000;
- efectivo esperado de la caja: + Gs. 50.000;
- ventas nuevas: Gs. 0;
- ganancia nueva: Gs. 0.
