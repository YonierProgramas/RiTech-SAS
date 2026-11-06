import { test, expect } from '../../fixtures/traceability.fixture';
import { jsonBody, expectForbidden } from '../../helpers/authorization';
import { ROLE_PROFILES } from '../../helpers/roles';
import { DEMO_TRACE_CODE, MISSING_TRACE_CODE, TRACE_NOT_FOUND,
  expectTraceContract, expectTraceOrder, readTrace, reservedTraceRelations, tracePath } from '../../helpers/traceability-data';
import type { TraceView } from '../../pages/TrazabilidadPage';

test.describe.configure({ mode: 'default' });

test('AUDITOR_PROCESO abre Trazabilidad con buscador real y resultado inicialmente vacío', async ({ traceModule }) => {
  await traceModule.expectInitialState();
});

test('La búsqueda del lote E2E muestra todos los eventos, tipos, descripciones y fechas de la API', async ({ traceModule, traceIntegrity, context }) => {
  const { lot } = reservedTraceRelations(traceIntegrity.before);
  const api = await readTrace(context.request, lot.codigo);

  const response = await traceModule.searchAndWaitForResponse(lot.codigo);

  expect(response.status()).toBe(200);
  const uiPayload = await jsonBody(response) as TraceView;
  expectTraceContract(uiPayload, lot.codigo);
  expect(uiPayload).toEqual(api);
  await traceModule.expectTrace(api);
  await traceModule.expectEventsPresent();
});

test('Los eventos E2E enlazan registro de lote, línea del pedido e inspección de calidad reales', async ({ traceModule, traceIntegrity, context }) => {
  const { lot, order, orderDetail, inspection } = reservedTraceRelations(traceIntegrity.before);
  const view = await readTrace(context.request, lot.codigo);
  const expected = [
    { origen_evento: 'LOTE', referencia_id: lot.id, tipo_evento: 'REGISTRO_LOTE',
      descripcion: `Registro del lote: ${lot.codigo}. Origen: ${lot.origen}` },
    // referencia_id is detalle_pedido.id, not pedidos.id (audited SQL view).
    { origen_evento: 'PEDIDO', referencia_id: orderDetail.id, tipo_evento: 'ASOCIACION_PEDIDO',
      descripcion: `Pedido ${order.codigo}; cantidad: ${orderDetail.cantidadKg.toFixed(3)} kg` },
    { origen_evento: 'INSPECCION', referencia_id: inspection.id, tipo_evento: 'INSPECCION_CALIDAD',
      descripcion: `Resultado registrado: ${inspection.resultado}` },
  ];
  await traceModule.searchAndWaitForResponse(lot.codigo);
  await traceModule.expectTrace(view);
  for (const event of expected) {
    expect(view.eventos.filter(row => row.origen_evento === event.origen_evento)).toEqual([
      expect.objectContaining(event),
    ]);
    await traceModule.expectEventDescription(event.descripcion);
  }
});

test('El lote DEMO conserva sus eventos de exportación, documentos e hitos adicionales y coincide con la UI', async ({ traceModule, traceIntegrity, context }) => {
  const snapshot = traceIntegrity.before;
  const lot = snapshot.business.lotes.find(l => l.codigo === DEMO_TRACE_CODE);
  expect(lot, 'Precondición: lote DEMO de solo lectura').toBeDefined();
  const view = await readTrace(context.request, DEMO_TRACE_CODE);
  expect(new Set(view.eventos.map(e => e.origen_evento))).toEqual(new Set([
    'LOTE', 'PEDIDO', 'INSPECCION', 'ESTADO_EXPORTACION', 'DOCUMENTO', 'EVENTO_LOTE',
  ]));
  const relatedOrders = snapshot.orderDetails.filter(p => p.details.some(d => d.loteId === lot!.id));
  const orderCodes = snapshot.business.pedidos.filter(p => relatedOrders.some(d => d.id === p.id)).map(p => p.codigo);
  const exports = snapshot.exports.filter(exp => orderCodes.includes(exp.pedido));
  expect(exports.length).toBeGreaterThan(0);
  const expectedStates = exports.flatMap(exp => snapshot.exportDetails.find(d => d.id === exp.id)!.history.map(h =>
    expect.objectContaining({ origen_evento: 'ESTADO_EXPORTACION', referencia_id: h.id,
      tipo_evento: 'CAMBIO_ESTADO', descripcion: `Exportación ${exp.codigo}: ${h.estadoNuevo}. ${h.observacion}` })));
  expect(view.eventos.filter(e => e.origen_evento === 'ESTADO_EXPORTACION')).toEqual(expect.arrayContaining(expectedStates));
  const documents = view.eventos.filter(e => e.origen_evento === 'DOCUMENTO');
  expect(documents.length).toBeGreaterThan(0);
  for (const document of documents) {
    expect(document.tipo_evento).toBe('CARGA_DOCUMENTO');
    expect(exports.some(exp => document.descripcion.startsWith(`Exportación ${exp.codigo}; archivo `))).toBe(true);
    expect(document.descripcion).toMatch(/; versión \d+$/);
  }
  expect(view.eventos).toContainEqual(expect.objectContaining({ origen_evento: 'EVENTO_LOTE', tipo_evento: 'RECEPCION_BODEGA' }));
  const response = await traceModule.searchAndWaitForResponse(DEMO_TRACE_CODE);
  expect(await jsonBody(response)).toEqual(view);
  await traceModule.expectTrace(view);
});

test('El orden SQL por fecha, origen y referencia es estable y se conserva al presentar los eventos', async ({ traceModule, traceIntegrity, context }) => {
  const { lot } = reservedTraceRelations(traceIntegrity.before);
  for (const code of [lot.codigo, DEMO_TRACE_CODE]) {
    const first = await readTrace(context.request, code);
    expectTraceOrder(first.eventos);
    expect(await readTrace(context.request, code)).toEqual(first);
    const response = await traceModule.searchAndWaitForResponse(code);
    expect(await jsonBody(response)).toEqual(first);
    await traceModule.expectTrace(first);
  }
});

test('Código inexistente devuelve 400 JSON, muestra el error real y no inventa un resultado vacío exitoso', async ({ traceModule, traceIntegrity }) => {
  expect(traceIntegrity.before.business.lotes.some(l => l.codigo === MISSING_TRACE_CODE)).toBe(false);

  const response = await traceModule.searchAndWaitForResponse(MISSING_TRACE_CODE);

  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400, message: TRACE_NOT_FOUND });
  await traceModule.expectError(TRACE_NOT_FOUND);
  await traceModule.expectNoResults();
});

test('Código vacío activa required y no envía una consulta inválida', async ({ traceModule, page }) => {
  let queries = 0;
  page.on('request', r => { if (new URL(r.url()).pathname.startsWith('/api/trazabilidad/')) queries++; });

  await traceModule.search('');

  await traceModule.expectEmptySearchBlocked();
  await traceModule.expectNoResults();
  expect(queries).toBe(0);
});

test('Un código de espacios supera required pero recibe el rechazo real y conserva el resultado previo identificado', async ({ traceModule, traceIntegrity }) => {
  const { lot, view } = reservedTraceRelations(traceIntegrity.before);
  await traceModule.searchAndWaitForResponse(lot.codigo);
  await traceModule.expectTrace(view);

  const response = await traceModule.searchAndWaitForResponse('   ');

  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400, message: TRACE_NOT_FOUND });
  await traceModule.expectError(TRACE_NOT_FOUND);
  // The real catch handler only shows a toast; it does not replace an earlier result.
  await traceModule.expectTrace(view);
});

test('Ventas no ve Trazabilidad y la consulta directa del lote E2E recibe 403 JSON', async ({ signInAs, dashboardPage, context, traceIntegrity }) => {
  await signInAs('ventas');
  await dashboardPage.expectNavigation(ROLE_PROFILES.ventas.visibleModules);
  const { lot } = reservedTraceRelations(traceIntegrity.before);
  await expectForbidden(await context.request.get(tracePath(lot.codigo), { maxRedirects: 0 }),
    'ADMINISTRADOR_VENTAS GET trazabilidad del lote existente');
});
