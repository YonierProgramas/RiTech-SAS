import { test, expect } from '../../fixtures/ritech.fixture';
import { expectUnauthenticatedRedirect } from '../../helpers/authorization';

test('Logout por la UI destruye la sesión y vuelve a impedir el acceso protegido', async ({ signInAs, dashboardPage, loginPage, context, page }) => {
  await signInAs('exportaciones');

  await dashboardPage.logout();

  await loginPage.expectVisible();
  await expect(page).toHaveURL(/\/login\.html\?logout$/);
  const response = await context.request.get('/api/session', { maxRedirects: 0 });
  expectUnauthenticatedRedirect(response);
  await page.goto('/api/dashboard', { waitUntil: 'domcontentloaded' });
  await loginPage.expectVisible();
  await expect(dashboardPage.logoutButton).toHaveCount(0);
});
