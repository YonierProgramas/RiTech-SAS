# RIT-47: Pedidos y stock E2E acotado

## Contrato auditado

Fuentes: `pedidos()`/`roleSections` en `static/js/app.js`, `PedidoController`,
`PedidoService`, `InventarioController`, `InventarioService`, DTOs y entidades.
No se modifican estas fuentes de producción.

- Solo `ADMINISTRADOR_VENTAS` ve el menú Pedidos y puede crear pedidos.
- El formulario real permite un cliente activo, país destino, un lote activo,
  cantidad y observaciones. No hay selector independiente de producto.
- País y cantidad son obligatorios en HTML. Cantidad tiene min/step `0.001`;
  los selects no tienen `required`. El backend valida además país alfabético ISO-2,
  cliente existente/activo, lote existente/activo y disponibilidad.
- El selector de lotes filtra por estado ACTIVO; puede mostrar un lote con 0 kg.
  El backend rechaza su reserva, con mensaje visible en la UI.
- Tabla: Código, Cliente, Destino, Lote, Cantidad kg, Estado, Fecha.
  No hay botones de detalle, edición, confirmación, cancelación o cambio de estado.
- `GET /api/pedidos` y `GET /api/pedidos/{id}` permiten ventas, exportaciones y
  auditoría. El detalle es un **array de líneas**, con lote, producto y cantidad.
- `POST /api/pedidos` y `POST /api/pedidos/multiple` permiten solo ventas con CSRF.
  La creación múltiple existe únicamente en API; no se inventa un flujo por UI.
- El servicio genera `PED-XXXXXXXX`, confirma inmediatamente y reserva stock.
  No hay endpoint para cancelar/eliminar pedidos ni para liberar esa reserva.

## Doce escenarios

1. Ventas abre el módulo y sus controles reales de creación simple.
2. Siete columnas, número de filas y todos los resúmenes coinciden con API.
3. Opciones de clientes activos/lotes activos, IDs, nombres y disponibilidad real.
4. Crear por UI el único pedido reservado si falta, o reutilizarlo; recarga,
   persistencia, fila única y reserva exacta de 1 kg.
5. Comercial no ve el módulo; GET y POST directos reciben 403 JSON. El POST lleva
   CSRF válido, evitando atribuir a autorización un rechazo causado por falta de token.
6. País y cantidad vacíos: validación nativa, foco y ningún POST/persistencia.
7. Cantidad cero/negativa: `rangeUnderflow`, ningún POST ni cambio de stock.
8. Cliente inexistente: ID ausente del selector y 400 `Cliente no encontrado` por API.
9. Lote inexistente: ID ausente del selector y 400 `Lote no encontrado` por API.
10. País `1?`: POST real por UI, 400, mensaje visible y datos intactos.
11. Cantidad superior al disponible del lote propio: 400, mensaje visible y stock intacto.
12. Resumen visible y detalle API verifican relaciones, cantidad y estado.

Los casos 8 y 9 son comprobaciones complementarias de backend desde la sesión UI:
la interfaz no permite escribir IDs arbitrarios y no se inyectan opciones ficticias
ni se interceptan respuestas. El caso 12 comprueba el resumen real por UI y el
detalle por API; no pretende que exista una pantalla de detalle.

Al comparar el POST con el registro persistido, todos los campos deben coincidir.
Solo la fecha admite hasta 1 microsegundo de diferencia: `LocalDateTime` puede
incluir nanosegundos, mientras `fecha_pedido` es `DATETIME(6)`. La comprobación
convierte ambas fechas a nanosegundos y no elimina la validación del timestamp.

## Identidades persistentes y límites

| Recurso | Identificador reservado | Estado final |
| --- | --- | --- |
| Cliente | `E2E-RIT47-CLIENTE-UNICO` | Activo, `[E2E RIT-47] Cliente de pedidos` |
| Producto | `E2E-RIT47-PRODUCTO` | Activo, CAFE, KG |
| Lote | `E2E-RIT47-LOTE-UNICO` | ACTIVO, inicial 1 kg, reservado 1 kg, disponible 0 kg, despachado 0 kg |
| Pedido | Código generado por backend; se descubre por cliente/lote reservados | Único, CONFIRMADO, 1 kg, destino ES |

El correo del cliente es ficticio (`rit47-orders@ritech.invalid`). Las observaciones
indican que no debe despacharse ni exportarse. Los IDs/código se descubren por API,
no se fijan en el código ni se generan claves distintas por corrida.

La fixture comprueba las identidades completas disponibles en el contrato de lectura.
Una colisión de datos, pedido adicional, despacho, estado inesperado o reserva
inconsistente produce un fallo: no se sobrescriben datos ni se repone inventario.
La API de lotes no expone proveedor; su referencia se establece al crear, y la
verificación posterior usa código/producto/origen/fecha/cantidades/estado expuestos.

Si faltan recursos, el cliente se crea por UI en un contexto comercial aislado;
producto y lote se preparan mediante sus APIs reales en la sesión de ventas con
CSRF. El pedido se crea por UI, valida HTTP 200, mensaje `Pedido creado y lote
reservado`, fila, detalle persistido y reserva de stock. No se usan SQL ni mocks.

La primera creación **reserva exactamente 1 kg del lote ficticio**, sin despacharlo.
No se libera artificialmente: no existe esa operación en la API. Las siguientes
corridas reutilizan el pedido y no consumen stock adicional. El alta solo se ejecuta
si no hay pedido que use el cliente o lote reservado. Un estado parcial inesperado
falla sin crear más pedidos. Los negativos usan este lote agotado como barrera
adicional ante inserciones accidentales.

La fixture de módulo compara todos los clientes, productos, lotes y pedidos ajenos
a RIT-47 antes/después de cada escenario de ventas. Los negativos comparan además
el snapshot completo, incluidos los recursos propios. La fixture del pedido verifica
al terminar relaciones, una sola línea, cantidad, CONFIRMADO y stock final conocido.
Adjuntos del HTML report: `pedidos-integridad-protegida` y `pedido-e2e-lifecycle`.

Los archivos se ejecutan con **un worker**, también localmente, para que las
comparaciones de integridad de Clientes y Pedidos no coincidan con escrituras de
otro archivo sobre la misma base. No hay sesiones compartidas ni dependencia del
orden: cada fixture prepara o reutiliza lo necesario. Ejecutar una sola invocación
contra esta base a la vez; no hay bloqueo distribuido entre procesos independientes.

El cliente RIT-46 permanece separado e inactivo; no se reutiliza ni se reactiva.
No se modifican clientes/pedidos/lotes DEMO. No se crean exportaciones ni despachos.
El pedido E2E confirmado puede aparecer en el selector de exportaciones: no usarlo
para operación real, porque no hay una exclusión de E2E en el producto.

## Ejecución

Con RiTech arrancado manualmente y `.env.local` privado configurado:

```powershell
npx.cmd playwright test tests/e2e/orders
npx.cmd playwright test
npm.cmd run test:e2e:report
```

Credenciales: helpers existentes y variables `RITECH_E2E_*`, sin valores incrustados.
Login real por UI, contexto por test, sin `storageState`. Se conservan HTML report,
screenshots/trace/video en fallos. Primera corrida: creación real si falta; siguientes:
reutilización explícita en anotaciones y adjuntos, sin tests skipped.
