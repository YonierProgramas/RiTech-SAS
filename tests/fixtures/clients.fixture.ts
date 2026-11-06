import { test as base, expect } from './ritech.fixture';
import type { ClientesPage } from '../pages/ClientesPage';
import {
  DEDICATED_CLIENT, assertOwnedClient, dedicatedClientSnapshot, findOwnedClient,
  leaveDedicatedClientInactive, protectedClientsSnapshot, readClients, type ClientRecord,
} from '../helpers/clients-data';
import { jsonBody } from '../helpers/authorization';

type ClientsFixtures = {
  clientModule: ClientesPage;
  dedicatedClient: { record: ClientRecord; createdViaUi: boolean };
};

export const test = base.extend<ClientsFixtures>({
  clientModule: async ({ signInAs, clientesPage }, use) => {
    await signInAs('comercial');
    await clientesPage.open();
    await use(clientesPage);
  },
  dedicatedClient: async ({ clientModule, context }, use, testInfo) => {
    const beforeRows = await readClients(context.request);
    const protectedBefore = protectedClientsSnapshot(beforeRows);
    const existing = findOwnedClient(beforeRows);
    let createdViaUi = false;
    try {
      let record: ClientRecord;
      if (existing) {
        // Recover the known state after an interrupted earlier run, only for the owned fixture.
        record = (await leaveDedicatedClientInactive(context.request))!;
        await clientModule.open();
      } else {
        record = await base.step('Crear por UI el único cliente reservado RIT-46', async () => {
          await clientModule.fillForm({ ...DEDICATED_CLIENT });
          const response = await clientModule.saveAndWaitForResponse();
          expect(response.status()).toBe(200);
          createdViaUi = true;
          const saved = await jsonBody(response) as ClientRecord;
          assertOwnedClient(saved);
          expect(saved.activo).toBe(true);
          await clientModule.expectSuccess('Cliente registrado');
          await clientModule.expectClientRow(saved);
          const persisted = await dedicatedClientSnapshot(context.request);
          expect(persisted.id).toBe(saved.id);
          return persisted;
        });
      }
      testInfo.annotations.push({
        type: 'cliente-e2e',
        description: `id=${record.id}; fiscal=${record.identificacionFiscal}; ${createdViaUi ? 'creado por UI' : 'reutilizado'}; final=INACTIVO`,
      });
      await use({ record, createdViaUi });
    } finally {
      const final = await leaveDedicatedClientInactive(context.request);
      const afterRows = await readClients(context.request);
      expect(protectedClientsSnapshot(afterRows), 'Ningún cliente ajeno al fixture, incluidos los DEMO, debe cambiar').toEqual(protectedBefore);
      await testInfo.attach('cliente-e2e-lifecycle', {
        contentType: 'application/json',
        body: JSON.stringify({
          id: final?.id ?? null,
          identificacionFiscal: DEDICATED_CLIENT.identificacionFiscal,
          createdViaUi,
          existedBefore: Boolean(existing),
          newRecordPersisted: !existing && Boolean(final),
          finalActive: final?.activo ?? null,
          protectedClientsCompared: protectedBefore.length,
          protectedClientsUnchanged: true,
        }, null, 2),
      });
    }
  },
});

export { expect } from '@playwright/test';
