import { test, expect } from '../../fixtures/clients.fixture';
import { CLIENT_FIELD_NAMES } from '../../pages/ClientesPage';
import { DEDICATED_CLIENT, dedicatedClientSnapshot, readClients } from '../../helpers/clients-data';
import { expectForbidden, jsonBody } from '../../helpers/authorization';
import { ROLE_PROFILES } from '../../helpers/roles';

// One file, sequential cases, independent setup: no shared browser or order-dependent data.
test.describe.configure({ mode: 'default' });

test('GESTOR_COMERCIAL abre Clientes y dispone de los controles reales de alta', async ({ clientModule }) => {
  await clientModule.expectLoaded();
  await clientModule.expectCreationControls();
});

test('El listado presenta sus columnas y los datos reales devueltos por la API', async ({ clientModule, context }) => {
  const records = await readClients(context.request);

  await clientModule.expectListHeaders();
  await expect(clientModule.rows).toHaveCount(records.length);
  for (const record of records.slice(0, 3)) await clientModule.expectClientRow(record);
});

test('Exportaciones no ve Clientes y el backend rechaza su consulta directa', async ({ signInAs, dashboardPage, context }) => {
  await signInAs('exportaciones');
  await dashboardPage.expectNavigation(ROLE_PROFILES.exportaciones.visibleModules);

  const response = await context.request.get('/api/clientes', { maxRedirects: 0 });

  await expectForbidden(response, 'ESPECIALISTA_EXPORTACION GET /api/clientes');
});

test('El cliente dedicado se crea por UI si falta y persiste tras recargar sin duplicarse', async ({ clientModule, dedicatedClient, context }) => {
  const before = await dedicatedClientSnapshot(context.request);
  expect(before.id).toBe(dedicatedClient.record.id);
  await clientModule.expectClientRow(before);

  await clientModule.reloadList();

  await clientModule.expectClientRow(before);
  await expect(clientModule.rowByFiscalId(DEDICATED_CLIENT.identificacionFiscal)).toHaveCount(1);
  expect(await dedicatedClientSnapshot(context.request)).toEqual(before);
});

test('Todos los campos obligatorios vacíos bloquean el envío sin modificar el cliente', async ({ clientModule, dedicatedClient, page, context }) => {
  const before = await dedicatedClientSnapshot(context.request);
  let submissions = 0;
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/clientes') submissions++;
  });

  for (const field of CLIENT_FIELD_NAMES) {
    await clientModule.fillForm({ ...DEDICATED_CLIENT });
    await clientModule.clearField(field);
    await clientModule.save();
    await clientModule.expectRequiredField(field);
  }

  expect(submissions).toBe(0);
  expect(await dedicatedClientSnapshot(context.request)).toEqual(before);
  expect(dedicatedClient.record.id).toBe(before.id);
});

test('Un correo inválido muestra la validación nativa y no envía ni persiste datos', async ({ clientModule, dedicatedClient, page, context }) => {
  const before = await dedicatedClientSnapshot(context.request);
  let submissions = 0;
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/clientes') submissions++;
  });
  await clientModule.fillForm({ ...DEDICATED_CLIENT, email: 'correo-sin-arroba' });

  await clientModule.save();

  await clientModule.expectInvalidEmail();
  expect(submissions).toBe(0);
  expect(await dedicatedClientSnapshot(context.request)).toEqual(before);
  expect(dedicatedClient.record.id).toBe(before.id);
});

test('Un país de dos caracteres no alfabéticos recibe 400 y el mensaje de error real', async ({ clientModule, dedicatedClient, context }) => {
  const before = await dedicatedClientSnapshot(context.request);
  await clientModule.fillForm({ ...DEDICATED_CLIENT, pais: '1?' });

  const response = await clientModule.saveAndWaitForResponse();

  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400, message: expect.stringMatching(/^pais:/) });
  await clientModule.expectError(/^pais:/);
  expect(await dedicatedClientSnapshot(context.request)).toEqual(before);
  expect(dedicatedClient.record.id).toBe(before.id);
});

test('Una razón social de espacios supera required pero el backend la rechaza sin guardar', async ({ clientModule, dedicatedClient, context }) => {
  const before = await dedicatedClientSnapshot(context.request);
  await clientModule.fillForm({ ...DEDICATED_CLIENT, razonSocial: '   ' });

  const response = await clientModule.saveAndWaitForResponse();

  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400, message: expect.stringMatching(/^razonSocial:/) });
  await clientModule.expectError(/^razonSocial:/);
  expect(await dedicatedClientSnapshot(context.request)).toEqual(before);
  expect(dedicatedClient.record.id).toBe(before.id);
});

test('La identificación fiscal duplicada se rechaza ignorando mayúsculas y conserva el registro', async ({ clientModule, dedicatedClient, context }) => {
  const before = await dedicatedClientSnapshot(context.request);
  await clientModule.fillForm({
    ...DEDICATED_CLIENT,
    identificacionFiscal: DEDICATED_CLIENT.identificacionFiscal.toLowerCase(),
    razonSocial: '[E2E RIT-46] No debe reemplazar los datos',
  });

  const response = await clientModule.saveAndWaitForResponse();

  expect(response.status()).toBe(400);
  expect(await jsonBody(response)).toMatchObject({ status: 400, message: 'Ya existe un cliente con esa identificación fiscal' });
  await clientModule.expectError('Ya existe un cliente con esa identificación fiscal');
  expect(await dedicatedClientSnapshot(context.request)).toEqual(before);
  await expect(clientModule.rowByFiscalId(DEDICATED_CLIENT.identificacionFiscal)).toHaveCount(1);
  expect(dedicatedClient.record.id).toBe(before.id);
});
