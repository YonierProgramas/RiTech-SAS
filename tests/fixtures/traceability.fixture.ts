import { test as base, expect } from './ritech.fixture';
import type { TrazabilidadPage } from '../pages/TrazabilidadPage';
import { signInByUi } from '../helpers/ui-session';
import { traceSnapshot, type TraceSnapshot } from '../helpers/traceability-data';

type TraceFixtures = {
  traceIntegrity: { before: TraceSnapshot };
  traceModule: TrazabilidadPage;
};

export const test = base.extend<TraceFixtures>({
  // Independent read-only context also covers the test whose main page has a forbidden role.
  traceIntegrity: [async ({ browser, baseURL }, use, testInfo) => {
    expect(baseURL).toBeTruthy();
    const auditContext = await browser.newContext({ baseURL });
    try {
      await signInByUi(await auditContext.newPage(), 'auditoria');
      const before = await traceSnapshot(auditContext.request);
      try { await use({ before }); }
      finally {
        const after = await traceSnapshot(auditContext.request);
        expect(after, 'Trazabilidad conserva todos los registros, relaciones, stock y eventos consultados')
          .toEqual(before);
        await testInfo.attach('trazabilidad-integridad-lectura', {
          contentType: 'application/json', body: JSON.stringify({
            unchanged: true, newRecords: 0,
            businessRecords: Object.fromEntries(Object.entries(before.business).map(([key, rows]) => [key, rows.length])),
            inspections: before.inspections.length, exports: before.exports.length,
            orderDetails: before.orderDetails.reduce((sum, order) => sum + order.details.length, 0),
            traces: before.traces.map(trace => ({ code: trace.codigoLote, events: trace.eventos.length })),
          }, null, 2),
        });
      }
    } finally { await auditContext.close(); }
  }, { auto: true }],
  traceModule: async ({ signInAs, trazabilidadPage }, use) => {
    await signInAs('auditoria');
    await trazabilidadPage.open();
    await use(trazabilidadPage);
  },
});

export { expect } from '@playwright/test';
