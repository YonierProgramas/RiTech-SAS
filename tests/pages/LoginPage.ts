import { expect, type Locator, type Page, type Response } from '@playwright/test';

export class LoginPage {
  readonly form: Locator;
  readonly introHeading: Locator;
  readonly heading: Locator;
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;
  readonly message: Locator;

  constructor(private readonly page: Page) {
    this.form = page.locator('form[action="/login"]');
    this.introHeading = page.getByRole('heading', {
      name: 'Control, calidad y trazabilidad en un solo lugar.', exact: true,
    });
    this.heading = page.getByRole('heading', { name: 'Iniciar sesión', exact: true });
    this.emailInput = this.form.getByLabel('Correo', { exact: true });
    this.passwordInput = this.form.getByLabel('Contraseña', { exact: true });
    this.submitButton = this.form.getByRole('button', { name: 'Ingresar', exact: true });
    this.message = page.locator('#login-message');
  }

  async goto(): Promise<Response | null> {
    return this.page.goto('/login.html', { waitUntil: 'domcontentloaded' });
  }

  async openHome(): Promise<Response | null> {
    return this.page.goto('/', { waitUntil: 'domcontentloaded' });
  }

  /** Only submits the form; the caller chooses success or error assertions. */
  async login(email: string, password: string): Promise<Response> {
    await this.emailInput.fill(email);
    await this.passwordInput.fill(password);
    const [response] = await Promise.all([
      this.page.waitForResponse(r => new URL(r.url()).pathname === '/login' && r.request().method() === 'POST'),
      this.submit(),
    ]);
    return response;
  }

  async submit(): Promise<void> { await this.submitButton.click(); }

  async expectHidden(): Promise<void> { await expect(this.form).toHaveCount(0); }

  async expectRequiredField(field: 'email' | 'password'): Promise<void> {
    const input = field === 'email' ? this.emailInput : this.passwordInput;
    await expect(input).toHaveValue('');
    await expect(input).toBeFocused();
    expect(await input.evaluate((element: HTMLInputElement) => element.validity.valueMissing)).toBe(true);
    await expect(this.message).toHaveText('');
    await expect(this.page).toHaveURL(/\/login\.html$/);
  }

  async expectVisible(): Promise<void> {
    await expect(this.page).toHaveURL(/\/login\.html(?:\?.*)?$/);
    await expect(this.page).toHaveTitle('RiTech SAS | Iniciar sesión');
    await expect(this.introHeading).toBeVisible();
    await expect(this.heading).toBeVisible();
    await expect(this.emailInput).toBeEditable();
    await expect(this.passwordInput).toBeEditable();
    await expect(this.submitButton).toBeVisible();
  }

  async expectError(): Promise<void> {
    await this.expectVisible();
    await expect(this.page).toHaveURL(/\/login\.html\?error(?:&|$)/);
    await expect(this.message).toBeVisible();
    await expect(this.message).toHaveText('Credenciales incorrectas o usuario inactivo.');
  }
}
