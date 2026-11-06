import { test, expect } from '../fixtures/ritech.fixture';

test('LoginPage abre el login y permite editar el correo sin enviar credenciales', async ({ loginPage }) => {
  await loginPage.goto();
  await loginPage.expectVisible();

  await loginPage.emailInput.fill('pom.check@example.test');
  await expect(loginPage.emailInput).toHaveValue('pom.check@example.test');
  await loginPage.emailInput.clear();
  await expect(loginPage.emailInput).toHaveValue('');
});

test('LoginPage reconoce la vista de error real sin intentar autenticarse', async ({ page, loginPage }) => {
  // This checks presentation of the existing redirect target, not a backend login attempt.
  await page.goto('/login.html?error', { waitUntil: 'domcontentloaded' });
  await loginPage.expectError();
});
