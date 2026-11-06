import { test, expect } from '../../fixtures/orders.fixture';
import { csrfHeaders, expectForbidden, jsonBody } from '../../helpers/authorization';
import { ROLE_PROFILES } from '../../helpers/roles';
import { businessSnapshot, expectOrderDetails, readList, type OrderRecord } from '../../helpers/orders-data';

test.describe.configure({ mode: 'default' });

test('ADMINISTRADOR_VENTAS abre Pedidos y sus controles reales de creación simple', async ({ orderModule }) => {
  await orderModule.expectLoaded();
  await orderModule.expectCreationControls();
});

test('El listado presenta sus siete columnas y cada resumen coincide con la API', async ({ orderModule, context }) => {
  const orders = await readList<OrderRecord>(context.request, '/api/pedidos');
  await orderModule.expectListHeaders();
  await expect(orderModule.rows).toHaveCount(orders.length);
  for (const order of orders) await orderModule.expectOrderSummary(order);
});

test('Los selectores muestran solo clientes activos y lotes activos con disponibilidad real', async ({ orderModule, context }) => {
  const snapshot = await businessSnapshot(context.request);
  await orderModule.expectOptions(snapshot.clientes, snapshot.lotes);
});

test('El pedido E2E se crea una sola vez por UI y persiste al recargar con reserva de 1 kg', async ({ orderModule, dedicatedOrder, context }) => {
  const before = await businessSnapshot(context.request);
  await orderModule.expectOrderSummary(dedicatedOrder.order);

  await orderModule.reloadList();

  await orderModule.expectOrderSummary(dedicatedOrder.order);
  await expect(orderModule.summaryByCode(dedicatedOrder.order.codigo)).toHaveCount(1);
  expect(await businessSnapshot(context.request)).toEqual(before);
  const lot = before.lotes.find(l => l.id === dedicatedOrder.lot.id)!;
  expect(lot).toMatchObject({ cantidadInicialKg: 1, cantidadReservadaKg: 1, cantidadDisponibleKg: 0, cantidadDespachadaKg: 0 });
});

test('Comercial no ve Pedidos y el backend rechaza lectura y creación con CSRF válido', async ({ signInAs, dashboardPage, context }) => {
  await signInAs('comercial');
  await dashboardPage.expectNavigation(ROLE_PROFILES.comercial.visibleModules);
  await expectForbidden(await context.request.get('/api/pedidos', { maxRedirects: 0 }), 'GESTOR_COMERCIAL GET pedidos');
  const response = await context.request.post('/api/pedidos', { maxRedirects: 0,
    headers: await csrfHeaders(context.request),
    // IDs inexistentes, incluso ante una regresión de autorización no se toca stock real.
    data: { clienteId: 0, loteId: 0, paisDestino: 'ES', cantidadKg: 1 } });
  await expectForbidden(response, 'GESTOR_COMERCIAL POST pedidos con CSRF');
});

test('País destino y cantidad obligatorios impiden enviar el formulario incompleto', async ({ orderModule, dedicatedOrder, page, context }) => {
  const before = await businessSnapshot(context.request);
  let submissions = 0;
  page.on('request', r => { if (r.method() === 'POST' && new URL(r.url()).pathname === '/api/pedidos') submissions++; });
  for (const field of ['paisDestino', 'cantidadKg'] as const) {
    await orderModule.fillForm(dedicatedOrder.form);
    await orderModule[field].clear();
    await orderModule.create();
    await orderModule.expectMissingRequired(field);
  }
  expect(submissions).toBe(0);
  expect(await businessSnapshot(context.request)).toEqual(before);
});

test('Cantidad cero o negativa activa min y no envía ni crea pedidos', async ({ orderModule, dedicatedOrder, page, context }) => {
  const before = await businessSnapshot(context.request);
  let submissions = 0;
  page.on('request', r => { if (r.method() === 'POST' && new URL(r.url()).pathname === '/api/pedidos') submissions++; });
  for (const quantity of [0, -1]) {
    await orderModule.fillForm({ ...dedicatedOrder.form, cantidadKg: quantity });
    await orderModule.create();
    await orderModule.expectQuantityUnderflow();
  }
  expect(submissions).toBe(0);
  expect(await businessSnapshot(context.request)).toEqual(before);
});

test('Cliente inexistente: el selector no lo ofrece y la API rechaza el ID sin persistencia', async ({ orderModule, dedicatedOrder, context }) => {
  const before = await businessSnapshot(context.request);
  expect(await orderModule.cliente.getByRole('option').evaluateAll(options => options.map(o => (o as HTMLOptionElement).value))).not.toContain('0');
  // Companion backend check: the real UI cannot choose an arbitrary nonexistent ID.
  const response = await context.request.post('/api/pedidos', { maxRedirects: 0,
    headers: await csrfHeaders(context.request), data: { ...dedicatedOrder.form, clienteId: 0 } });
  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400, message: 'Cliente no encontrado' });
  expect(await businessSnapshot(context.request)).toEqual(before);
});

test('Lote inexistente: el selector no lo ofrece y la API rechaza el ID sin reservar stock', async ({ orderModule, dedicatedOrder, context }) => {
  const before = await businessSnapshot(context.request);
  expect(await orderModule.lote.getByRole('option').evaluateAll(options => options.map(o => (o as HTMLOptionElement).value))).not.toContain('0');
  const response = await context.request.post('/api/pedidos', { maxRedirects: 0,
    headers: await csrfHeaders(context.request), data: { ...dedicatedOrder.form, loteId: 0 } });
  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400, message: 'Lote no encontrado' });
  expect(await businessSnapshot(context.request)).toEqual(before);
});

test('País destino no alfabético se rechaza con 400 y mensaje visible sin crear pedido', async ({ orderModule, dedicatedOrder, context }) => {
  const before = await businessSnapshot(context.request);
  await orderModule.fillForm({ ...dedicatedOrder.form, paisDestino: '1?' });

  const response = await orderModule.createAndWaitForResponse();

  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400, message: expect.stringMatching(/^paisDestino:/) });
  await orderModule.expectError(/^paisDestino:/);
  expect(await businessSnapshot(context.request)).toEqual(before);
});

test('Stock insuficiente del lote E2E muestra el error real y conserva pedidos y cantidades', async ({ orderModule, dedicatedOrder, context }) => {
  const before = await businessSnapshot(context.request);
  const lot = before.lotes.find(l => l.id === dedicatedOrder.lot.id)!;
  await orderModule.fillForm({ ...dedicatedOrder.form, cantidadKg: lot.cantidadDisponibleKg + 1 });

  const response = await orderModule.createAndWaitForResponse();

  expect(response.status()).toBe(400);
  const message = 'La cantidad solicitada supera la disponibilidad del lote';
  expect(await jsonBody(response)).toMatchObject({ status: 400, message });
  await orderModule.expectError(message);
  expect(await businessSnapshot(context.request)).toEqual(before);
});

test('Resumen visible y detalle real de API conservan cliente, lote, producto, cantidad y estado', async ({ orderModule, dedicatedOrder, context }) => {
  const before = await businessSnapshot(context.request);
  await orderModule.expectOrderSummary(dedicatedOrder.order);
  await expectOrderDetails(context.request, dedicatedOrder.order, dedicatedOrder);
  // No modal/button is invented: GET /api/pedidos/{id} exists, the UI exposes only the summary.
  expect(await businessSnapshot(context.request)).toEqual(before);
});
