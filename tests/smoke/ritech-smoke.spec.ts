import { test, expect } from '../fixtures/ritech.fixture';

test('RiTech responde y presenta el formulario de inicio de sesión', async ({ page, loginPage }) => {
  const response = await loginPage.openHome();

  expect(response, 'RiTech debe devolver una respuesta HTTP').not.toBeNull();
  expect(response!.status()).toBe(200);
  expect(response!.headers()['content-type']).toContain('text/html');
  await expect(page).toHaveURL(/\/login\.html$/);
  await loginPage.expectVisible();
});
