import { test as base, expect } from './ritech.fixture';
import type { APIRequestContext } from '@playwright/test';
import type { CalidadPage, InspeccionFormData } from '../pages/CalidadPage';
import { signInByUi, type AuthenticatedSession } from '../helpers/ui-session';
import { jsonBody } from '../helpers/authorization';
import { readList, type LotRecord } from '../helpers/orders-data';
import {
  qualitySnapshot, qualityLot, findQualityInspection, inspectionForm, expectQualityIntegrity, expectPersistedInspection,
  type InspectionRecord, type QualitySnapshot,
} from '../helpers/quality-data';

type QualityFixtures = {
  qualityIntegrity: { request: APIRequestContext; before: QualitySnapshot };
  qualitySession: AuthenticatedSession;
  qualityModule: CalidadPage;
  dedicatedInspection: { record: InspectionRecord; lot: LotRecord; form: InspeccionFormData; createdViaUi: boolean };
};

export const test = base.extend<QualityFixtures>({
  // Inspector cannot read clients/products/orders; an isolated read-only auditor verifies integrity.
  qualityIntegrity: [async ({ browser, baseURL }, use, testInfo) => {
    expect(baseURL).toBeTruthy();
    const auditContext = await browser.newContext({ baseURL });
    try {
      await signInByUi(await auditContext.newPage(), 'auditoria');
      const before = await qualitySnapshot(auditContext.request);
      try { await use({ request: auditContext.request, before }); }
      finally {
        const after = await qualitySnapshot(auditContext.request);
        const owned = expectQualityIntegrity(before, after);
        await testInfo.attach('calidad-integridad-protegida', {
          contentType: 'application/json', body: JSON.stringify({
            businessUnchanged: true, orderDetailsUnchanged: true, originalInspectionsUnchanged: true,
            originalInspections: before.inspections.length,
            businessRecords: Object.fromEntries(Object.entries(before.business).map(([key, rows]) => [key, rows.length])),
            newInspections: after.inspections.length - before.inspections.length,
            inspectionId: owned?.id ?? null, result: owned?.resultado ?? null,
          }, null, 2),
        });
      }
    } finally { await auditContext.close(); }
  }, { auto: true }],
  qualitySession: async ({ signInAs }, use) => { await use(await signInAs('calidad')); },
  qualityModule: async ({ qualitySession, calidadPage }, use) => {
    expect(qualitySession.rol).toBe('INSPECTOR_CALIDAD');
    await calidadPage.open();
    await use(calidadPage);
  },
  dedicatedInspection: async ({ qualityModule, qualitySession, qualityIntegrity, context }, use, testInfo) => {
    const lot = qualityLot(qualityIntegrity.before.business.lotes);
    const initial = await readList<InspectionRecord>(context.request, '/api/calidad');
    let record = findQualityInspection(initial, lot, qualitySession.nombre);
    let createdViaUi = false;
    let ready = false;
    try {
      const form = inspectionForm(lot);
      if (!record) {
        record = await base.step('Crear por UI la única inspección RIT-48 del lote propio RIT-47', async () => {
          await qualityModule.fillForm(form);
          const response = await qualityModule.registerAndWaitForResponse();
          expect(response.status()).toBe(200);
          createdViaUi = true;
          const saved = await jsonBody(response) as InspectionRecord;
          await qualityModule.expectSuccess();
          await qualityModule.expectInspectionRows([saved], qualityModule.rowByIdentity(lot.codigo, form.observaciones!));
          const stored = findQualityInspection(await readList<InspectionRecord>(context.request, '/api/calidad'), lot, qualitySession.nombre);
          expect(stored).toBeDefined();
          expectPersistedInspection(saved, stored!);
          return stored!;
        });
      }
      testInfo.annotations.push({ type: 'inspeccion-e2e',
        description: `id=${record.id}; lote=${lot.codigo}; ${createdViaUi ? 'creada por UI' : 'reutilizada'}; APROBADO; stock sin cambios` });
      ready = true;
      await use({ record, lot, form, createdViaUi });
    } finally {
      const final = findQualityInspection(await readList<InspectionRecord>(context.request, '/api/calidad'), lot, qualitySession.nombre);
      if (ready) expect(final).toEqual(record);
      await testInfo.attach('inspeccion-e2e-lifecycle', {
        contentType: 'application/json', body: JSON.stringify({ id: final?.id ?? null, loteId: lot.id, lote: lot.codigo,
          createdViaUi, existedBefore: Boolean(findQualityInspection(initial, lot, qualitySession.nombre)),
          newInspections: createdViaUi ? 1 : 0, result: final?.resultado ?? null,
          observations: final?.observaciones ?? null, inspector: final?.inspector ?? null }, null, 2),
      });
    }
  },
});

export { expect } from '@playwright/test';
