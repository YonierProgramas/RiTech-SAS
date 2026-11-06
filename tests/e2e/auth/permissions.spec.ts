import { test, expect } from '../../fixtures/ritech.fixture';
import { ROLE_KEYS, ROLE_PROFILES } from '../../helpers/roles';
import { expectForbidden, jsonBody, probeDocumentUploadPermission } from '../../helpers/authorization';

for (const role of ROLE_KEYS) {
  const profile = ROLE_PROFILES[role];
  test(`Permisos reales de ${profile.role}: menú, módulos y protección backend`, async ({ signInAs, dashboardPage, trazabilidadPage, context }) => {
    await signInAs(role);
    await dashboardPage.expectNavigation(profile.visibleModules);

    for (const module of profile.visibleModules) {
      if (module === 'dashboard') continue;
      await dashboardPage.openModule(module);
      await dashboardPage.expectModuleLoaded(module);
    }
    if (role === 'auditoria') {
      const code = await trazabilidadPage.lotCode.inputValue();
      expect(code.trim().length).toBeGreaterThan(0);
      await trazabilidadPage.search(code);
      await trazabilidadPage.expectResult(code);
      await trazabilidadPage.expectEventsPresent();
    }

    // Same browser cookie jar: menu visibility alone is not an authorization check.
    for (const path of profile.allowedReads) {
      const response = await context.request.get(path, { maxRedirects: 0 });
      expect(response.status(), `${profile.role} GET ${path}`).toBe(200);
      expect(Array.isArray(await jsonBody(response, path)), `${path}: listado JSON real`).toBe(true);
    }
    for (const path of profile.forbiddenReads) {
      const response = await context.request.get(path, { maxRedirects: 0 });
      await expectForbidden(response, `${profile.role} GET ${path}`);
    }

    // Valid CSRF and a present but empty file distinguish authorization from CSRF/argument failures.
    const upload = await probeDocumentUploadPermission(context.request);
    if (role === 'exportaciones') {
      expect(upload.status(), 'El especialista llega al rechazo de archivo vacío, sin guardar datos').toBe(400);
      expect(await jsonBody(upload)).toMatchObject({ status: 400, message: 'Debe seleccionar un archivo' });
    } else {
      await expectForbidden(upload, `${profile.role} POST documentos con CSRF válido`);
    }
  });
}
