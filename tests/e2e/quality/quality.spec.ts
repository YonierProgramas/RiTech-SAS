import type { APIRequestContext } from '@playwright/test';
import { test, expect } from '../../fixtures/quality.fixture';
import { csrfHeaders, expectForbidden, jsonBody } from '../../helpers/authorization';
import { readList, type LotRecord } from '../../helpers/orders-data';
import { qualitySnapshot, expectDescendingInspectionDates, type InspectionRecord } from '../../helpers/quality-data';
import { ROLE_PROFILES } from '../../helpers/roles';

test.describe.configure({ mode: 'default' });

async function expectRejectedInspection(request: APIRequestContext, data: object, message: string | RegExp): Promise<void> {
  const response = await request.post('/api/calidad', { maxRedirects: 0, headers: await csrfHeaders(request), data });
  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400,
    message: typeof message === 'string' ? message : expect.stringMatching(message) });
}

test('INSPECTOR_CALIDAD abre Calidad y sus controles reales sin inventar campos required', async ({ qualityModule }) => {
  await qualityModule.expectLoaded();
  await qualityModule.expectCreationControls();
});

test('El listado de inspecciones y sus seis columnas coinciden con API en orden de fecha descendente', async ({ qualityModule, context }) => {
  const records = await readList<InspectionRecord>(context.request, '/api/calidad');
  expectDescendingInspectionDates(records);
  await qualityModule.expectListHeaders();
  await qualityModule.expectInspectionRows(records);
});

test('Los selectores muestran los lotes reales y únicamente los tres resultados permitidos', async ({ qualityModule, context }) => {
  await qualityModule.expectOptions(await readList<LotRecord>(context.request, '/api/lotes'));
});

test('La inspección E2E se crea una sola vez por UI y persiste al recargar sin cambiar el lote', async ({ qualityModule, dedicatedInspection, context, qualityIntegrity }) => {
  const before = await qualitySnapshot(qualityIntegrity.request);
  await qualityModule.expectInspectionRows([dedicatedInspection.record],
    qualityModule.rowByIdentity(dedicatedInspection.lot.codigo, dedicatedInspection.form.observaciones!));

  await qualityModule.reloadList();

  await qualityModule.expectInspectionRows([dedicatedInspection.record],
    qualityModule.rowByIdentity(dedicatedInspection.lot.codigo, dedicatedInspection.form.observaciones!));
  await qualityModule.expectResultForLot(dedicatedInspection.lot.codigo, 'APROBADO');
  const records = await readList<InspectionRecord>(context.request, '/api/calidad');
  expect(records.filter(i => i.id === dedicatedInspection.record.id)).toEqual([dedicatedInspection.record]);
  expect(await qualitySnapshot(qualityIntegrity.request)).toEqual(before);
});

test('Comercial no ve Calidad y recibe 403 en listado, historial y POST con CSRF válido', async ({ signInAs, dashboardPage, context }) => {
  await signInAs('comercial');
  await dashboardPage.expectNavigation(ROLE_PROFILES.comercial.visibleModules);
  await expectForbidden(await context.request.get('/api/calidad', { maxRedirects: 0 }), 'GESTOR_COMERCIAL GET calidad');
  await expectForbidden(await context.request.get('/api/calidad/lotes/0', { maxRedirects: 0 }), 'GESTOR_COMERCIAL GET historial');
  await expectForbidden(await context.request.post('/api/calidad', {
    maxRedirects: 0, headers: await csrfHeaders(context.request), data: { loteId: 0, resultado: 'APROBADO' },
  }), 'GESTOR_COMERCIAL POST calidad con CSRF');
});

test('El backend exige lote y resultado y rechaza un resultado ajeno a las opciones sin persistir', async ({ qualityModule, context, qualityIntegrity }) => {
  const before = await qualitySnapshot(qualityIntegrity.request);
  // No required attributes in UI: do not invent native validation. Exercise the mandatory DTO contract.
  await expectRejectedInspection(context.request, { loteId: null, resultado: 'APROBADO' }, /^loteId:/);
  for (const resultado of [null, '', 'DESCONOCIDO']) {
    // If validation regresses, nonexistent lot 0 still prevents insertion.
    await expectRejectedInspection(context.request, { loteId: 0, resultado }, /^resultado:/);
  }
  await expect(qualityModule.resultado.getByRole('option')).toHaveText(['PENDIENTE', 'APROBADO', 'RECHAZADO']);
  expect(await qualitySnapshot(qualityIntegrity.request)).toEqual(before);
});

test('Lote inexistente no aparece en el selector y su inspección e historial se rechazan', async ({ qualityModule, context, qualityIntegrity }) => {
  const before = await qualitySnapshot(qualityIntegrity.request);
  expect(await qualityModule.lote.getByRole('option').evaluateAll(options => options.map(o => (o as HTMLOptionElement).value)))
    .not.toContain('0');
  await expectRejectedInspection(context.request, { loteId: 0, resultado: 'APROBADO' }, 'Lote no encontrado');
  const history = await context.request.get('/api/calidad/lotes/0', { maxRedirects: 0 });
  expect(history.status()).toBe(400);
  expect(await jsonBody(history)).toMatchObject({ status: 400, message: 'Lote no encontrado' });
  expect(await qualitySnapshot(qualityIntegrity.request)).toEqual(before);
});

test('Humedad fuera de 0 a 100 se bloquea por UI y el backend también la rechaza sin guardar', async ({ qualityModule, dedicatedInspection, page, context, qualityIntegrity }) => {
  const before = await qualitySnapshot(qualityIntegrity.request);
  let submissions = 0;
  page.on('request', r => { if (r.method() === 'POST' && new URL(r.url()).pathname === '/api/calidad') submissions++; });
  for (const value of [-0.01, 100.01]) {
    await qualityModule.fillForm({ ...dedicatedInspection.form, humedadPorcentaje: value });
    await qualityModule.register();
    await qualityModule.expectHumidityRangeError(value < 0 ? 'underflow' : 'overflow');
  }
  expect(submissions).toBe(0);
  for (const humedadPorcentaje of [-0.01, 100.01]) {
    await expectRejectedInspection(context.request, { loteId: 0, humedadPorcentaje, resultado: 'APROBADO' }, /^humedadPorcentaje:/);
  }
  expect(await qualitySnapshot(qualityIntegrity.request)).toEqual(before);
});

test('Taza y aroma de más de 150 caracteres muestran el error del backend y no crean inspecciones', async ({ qualityModule, dedicatedInspection, qualityIntegrity }) => {
  const before = await qualitySnapshot(qualityIntegrity.request);
  for (const field of ['evaluacionTaza', 'aroma'] as const) {
    await qualityModule.fillForm({ ...dedicatedInspection.form, [field]: 'X'.repeat(151) });

    const response = await qualityModule.registerAndWaitForResponse();

    expect(response.status()).toBe(400);
    expect(await jsonBody(response)).toMatchObject({ status: 400, message: expect.stringMatching(new RegExp(`^${field}:`)) });
    await qualityModule.expectError(new RegExp(`^${field}:`));
  }
  expect(await qualitySnapshot(qualityIntegrity.request)).toEqual(before);
});

test('Historial por lote incluye la inspección E2E y coincide con las filas visibles y la API general', async ({ qualityModule, dedicatedInspection, context }) => {
  const history = await readList<InspectionRecord>(context.request, `/api/calidad/lotes/${dedicatedInspection.lot.id}`);
  const all = await readList<InspectionRecord>(context.request, '/api/calidad');
  expect(history).toEqual(all.filter(i => i.loteId === dedicatedInspection.lot.id));
  expect(history.every(i => i.loteId === dedicatedInspection.lot.id)).toBe(true);
  expect(history).toContainEqual(dedicatedInspection.record);
  expectDescendingInspectionDates(history);
  // The UI exposes the global table, not a separate history button/filter.
  await qualityModule.expectInspectionRows(history, qualityModule.rowsForLot(dedicatedInspection.lot.codigo));
});
