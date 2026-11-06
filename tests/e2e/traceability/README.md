# Trazabilidad: UI/API y relaciones de solo lectura

## Contrato auditado

Fuentes: `trazabilidad()`/`roleSections` en `static/js/app.js`,
`TrazabilidadController`, `TrazabilidadService`, `TrazabilidadView`,
`v_trazabilidad_lote` y los GET utilizados para comprobar relaciones/integridad.
Estas fuentes de producción no se modifican.

- Solo AUDITOR_PROCESO ve el módulo y puede consultar `GET /api/trazabilidad/{codigoLote}`.
- La SPA ofrece un único campo sin label, placeholder `Código del lote`, atributo
  required y valor inicial `DEMO-LOTE-001`; el botón es `Consultar`.
  El POM usa el único textbox dentro del formulario existente `#trace-form`.
- Abrir el módulo no consulta automáticamente el código precargado; el resultado
  está inicialmente vacío. No hay filtros por tipo/fecha, paginación, edición,
  creación de eventos, drill-down de relaciones ni botón de exportar.
- JSON: `{ codigoLote, eventos: [...] }`. Cada evento tiene `origen_evento`,
  `referencia_id`, `tipo_evento`, `fecha_evento`, `usuario_id`, `descripcion`.
  La tabla muestra **Fecha, Origen, Evento, Descripción**; no muestra IDs de
  referencia/usuario. Estos se comprueban por API donde corresponde.
- El servicio usa un parámetro SQL y ordena por fecha, origen y referencia ASC.
  La vista agrega datos de lote, líneas de pedido, inspecciones, historial de
  estados de exportación, documentos de todas las versiones e hitos adicionales.
- La referencia de un evento PEDIDO es **detalle_pedido.id**, no pedidos.id.
  INSPECCION referencia la inspección; LOTE referencia el lote.
- Un código sin eventos produce **400 JSON**, con mensaje
  `No se encontró trazabilidad para el lote indicado`. No es 404 ni 200 vacío.
  Todo lote existente aporta REGISTRO_LOTE; no se crea otro lote para simular un
  estado de cero eventos que la vista no representa.
- No hay validación de formato por regex ni normalización/trim en el controlador
  o formulario. No se inventan restricciones de formato ni de mayúsculas.
- Vacío: required impide enviar. Espacios: supera required, se consulta el código
  literal codificado y se obtiene el error de negocio real.
- El catch de la UI muestra el toast y conserva cualquier resultado previo.
  El test verifica tanto el mensaje como el encabezado/celdas anteriores; no
  confunde ese resultado conservado con una búsqueda nueva exitosa.

## Nueve escenarios

1. Auditor abre el módulo, reconoce buscador/default/required y resultado vacío.
2. Lote E2E: HTTP 200 JSON, misma respuesta UI/API, cantidad de eventos y todas las
   celdas (fecha, origen, tipo y descripción) en el mismo orden.
3. Registro de lote, asociación a la línea del pedido e inspección corresponden a
   los IDs/descripciones/cantidad/resultado reales de las APIs relacionadas.
4. DEMO-LOTE-001: seis orígenes reales, relaciones con exportaciones/historial,
   documentos/versiones e hitos existentes, y tabla idéntica a la API.
5. Orden SQL y repetibilidad en E2E/DEMO; la UI conserva el orden recibido.
   La fecha se compara con toda su fracción, no solo con los milisegundos visibles.
6. Código fijo inexistente: 400 y toast exacto, sin presentar un éxito vacío.
7. Campo vacío: validación nativa, foco y cero consultas de trazabilidad.
8. Código de espacios tras un éxito: 400, error visible y resultado anterior
   correctamente identificado y sin cambios.
9. Ventas no ve el módulo y recibe 403 JSON al consultar directamente el lote E2E.

Para fechas visibles se usa `new Date(...).toLocaleString()` dentro del navegador,
igual que el frontend. No se impone otro huso ni se compara literalmente una fecha
JDBC con un DTO LocalDateTime de otro módulo. Las relaciones se verifican por las
referencias y descripciones del contrato real.

## Datos y protección

Recurso principal: **E2E-RIT47-LOTE-UNICO**, con su pedido RIT-47 e inspección
RIT-48 preexistentes. Sus IDs se descubren por API, sin fijarlos en el código.
DEMO-LOTE-001 se utiliza únicamente para leer tipos de eventos adicionales que el
lote E2E no tiene. No se crean exportaciones, documentos ni eventos para ampliar
la cobertura. Precondiciones ausentes fallan explícitamente; no hay skips ni seeds.

La fixture automática de integridad inicia por UI un auditor aislado de solo lectura
y compara el snapshot completo antes/después de **cada** escenario, incluso el de
rol no autorizado. Incluye:

- Clientes, productos, lotes/stock, pedidos y sus líneas.
- Inspecciones completas, incluidas DEMO y la E2E existente.
- Exportaciones, historial, checklist vigente y trámites.
- Trazabilidad completa de todos los lotes: todos los campos de todos los eventos.

Los helpers de negocio solo realizan GET. La única escritura de las sesiones es
el login real mediante POST /login; no se envían escrituras a APIs de negocio.
No hay SQL directo, mocks, cambios de producción ni datos artificiales persistidos.
El adjunto HTML `trazabilidad-integridad-lectura` registra conteos y el resultado
de la comparación exacta. No se usa storageState ni se comparten sesiones.

Se conserva el worker único. Ejecutar una sola invocación contra esta base a la vez,
para que escrituras de otro proceso no alteren los snapshots durante la comparación.

## Ejecución

Con RiTech arrancado manualmente y `.env.local` privado configurado:

```powershell
npx.cmd playwright test tests/e2e/traceability
npx.cmd playwright test
npm.cmd run test:e2e:report
```

En una base sin recursos E2E, preparar primero los bloques Pedidos y Calidad; esta
suite no los crea. HTML en `playwright-report/index.html`, screenshot/trace/video
en fallos, credenciales por los helpers existentes. Backend, seguridad, esquema y
Postman/Newman se mantienen intactos.
