import { test, expect } from '../../fixtures/ritech.fixture';
import { getRiTechCredentials } from '../../helpers/credentials';
import { expectUnauthenticatedRedirect } from '../../helpers/authorization';

test('Login válido por UI redirige al dashboard e identifica al usuario y su rol', async ({ signInAs, dashboardPage, loginPage }) => {
  const session = await signInAs('exportaciones');

  await dashboardPage.expectLoaded({ nombre: session.nombre, rol: 'ESPECIALISTA_EXPORTACION' });
  await loginPage.expectHidden();
});

test('Contraseña incorrecta conserva el login y muestra el rechazo real', async ({ loginPage }) => {
  const credentials = getRiTechCredentials('exportaciones');
  const differentFirstCharacter = credentials.password.startsWith('!') ? '?' : '!';
  const wrongPassword = differentFirstCharacter + credentials.password.slice(1);
  await loginPage.goto();

  await loginPage.login(credentials.username, wrongPassword);

  await loginPage.expectError();
});

test('Usuario inexistente muestra el error real de credenciales', async ({ loginPage }) => {
  const credentials = getRiTechCredentials('exportaciones');
  await loginPage.goto();

  await loginPage.login('e2e-missing-user@ritech.invalid', credentials.password);

  await loginPage.expectError();
});

for (const missing of ['email', 'password'] as const) {
  test(`El campo obligatorio ${missing} vacío impide enviar el login`, async ({ page, loginPage }) => {
    const credentials = getRiTechCredentials('exportaciones');
    await loginPage.goto();
    let attempts = 0;
    page.on('request', request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname === '/login') attempts++;
    });
    if (missing === 'email') await loginPage.passwordInput.fill(credentials.password);
    else await loginPage.emailInput.fill(credentials.username);

    await loginPage.submit();

    await loginPage.expectRequiredField(missing);
    expect(attempts, 'El navegador no debe enviar POST /login con un campo requerido vacío').toBe(0);
  });
}

test('Una sesión limpia no puede abrir /api/session y termina en el login visible', async ({ context, page, loginPage }) => {
  const response = await context.request.get('/api/session', { maxRedirects: 0 });
  expectUnauthenticatedRedirect(response);

  await page.goto('/api/session', { waitUntil: 'domcontentloaded' });

  await loginPage.expectVisible();
});
