import { expect, type Locator, type Response } from '@playwright/test';
import { AppPage } from './AppPage';

export type ClienteFormData = {
  razonSocial: string;
  pais: string;
  identificacionFiscal: string;
  email: string;
  telefono: string;
  contactoPrincipal: string;
  direccion: string;
};

export const CLIENT_FIELD_NAMES = ['razonSocial', 'pais', 'identificacionFiscal', 'email',
  'telefono', 'contactoPrincipal', 'direccion'] as const satisfies readonly (keyof ClienteFormData)[];
export type ClienteField = typeof CLIENT_FIELD_NAMES[number];

export class ClientesPage extends AppPage {
  readonly heading = this.content.getByRole('heading', { name: 'Gestión de clientes', exact: true });
  readonly form = this.page.locator('#cliente-form');
  readonly razonSocial = this.form.getByLabel('Razón social', { exact: true });
  readonly pais = this.form.getByLabel('País (ISO-2)', { exact: true });
  readonly identificacionFiscal = this.form.getByLabel('Identificación fiscal', { exact: true });
  readonly email = this.form.getByLabel('Correo', { exact: true });
  readonly telefono = this.form.getByLabel('Teléfono', { exact: true });
  readonly contactoPrincipal = this.form.getByLabel('Contacto principal', { exact: true });
  readonly direccion = this.form.getByLabel('Dirección', { exact: true });
  readonly saveButton = this.form.getByRole('button', { name: 'Guardar cliente', exact: true });
  readonly table = this.tableWithColumn('Identificación');
  readonly rows = this.table.getByRole('row').filter({ has: this.page.getByRole('cell') });
  private readonly fields: Record<ClienteField, Locator> = {
    razonSocial: this.razonSocial, pais: this.pais, identificacionFiscal: this.identificacionFiscal,
    email: this.email, telefono: this.telefono, contactoPrincipal: this.contactoPrincipal, direccion: this.direccion,
  };

  async open(): Promise<void> {
    await this.openModule('clientes');
    await this.expectLoaded();
  }

  async expectLoaded(): Promise<void> {
    await expect(this.heading).toBeVisible();
    await expect(this.table).toBeVisible();
  }

  async expectCreationControls(): Promise<void> {
    for (const field of CLIENT_FIELD_NAMES) await expect(this.fields[field]).toBeEditable();
    await expect(this.saveButton).toBeVisible();
    await expect(this.pais).toHaveAttribute('maxlength', '2');
    await expect(this.email).toHaveAttribute('type', 'email');
  }

  async expectListHeaders(): Promise<void> {
    await expect(this.table.getByRole('columnheader')).toHaveText([
      'Razón social', 'País', 'Identificación', 'Correo', 'Contacto', 'Estado',
    ]);
  }

  async fillForm(data: ClienteFormData): Promise<void> {
    await this.razonSocial.fill(data.razonSocial);
    await this.pais.fill(data.pais);
    await this.identificacionFiscal.fill(data.identificacionFiscal);
    await this.email.fill(data.email);
    await this.telefono.fill(data.telefono);
    await this.contactoPrincipal.fill(data.contactoPrincipal);
    await this.direccion.fill(data.direccion);
  }

  async save(): Promise<void> { await this.saveButton.click(); }

  async saveAndWaitForResponse(): Promise<Response> {
    const [response] = await Promise.all([
      this.page.waitForResponse(r => new URL(r.url()).pathname === '/api/clientes' && r.request().method() === 'POST'),
      this.save(),
    ]);
    return response;
  }

  async clearField(field: ClienteField): Promise<void> { await this.fields[field].clear(); }

  async expectRequiredField(field: ClienteField): Promise<void> {
    const input = this.fields[field];
    await expect(input).toHaveValue('');
    await expect(input).toBeFocused();
    expect(await input.evaluate((element: HTMLInputElement) => element.validity.valueMissing)).toBe(true);
  }

  async expectInvalidEmail(): Promise<void> {
    await expect(this.email).toBeFocused();
    expect(await this.email.evaluate((element: HTMLInputElement) => element.validity.typeMismatch)).toBe(true);
  }

  async expectSuccess(message: string): Promise<void> {
    await this.expectMessage(message);
    await expect(this.notification).not.toHaveClass(/(?:^|\s)error(?:\s|$)/);
  }

  async expectError(message: string | RegExp): Promise<void> {
    await this.expectMessage(message);
    await expect(this.notification).toHaveClass(/(?:^|\s)error(?:\s|$)/);
  }

  async reloadList(): Promise<void> {
    await this.page.reload({ waitUntil: 'domcontentloaded' });
    // init() always renders the dashboard first; wait for it before selecting Clientes.
    await this.expectModuleLoaded('dashboard');
    await this.open();
  }

  rowByFiscalId(fiscalId: string): Locator {
    return this.table.getByRole('row').filter({
      has: this.page.getByRole('cell', { name: fiscalId, exact: true }),
    });
  }

  async expectClientListed(fiscalId: string): Promise<void> {
    await expect(this.rowByFiscalId(fiscalId)).toBeVisible();
  }

  async expectClientRow(client: ClienteFormData & { activo: boolean }): Promise<void> {
    const row = this.rowByFiscalId(client.identificacionFiscal);
    await expect(row).toBeVisible();
    await expect(row.getByRole('cell')).toHaveText([
      client.razonSocial, client.pais, client.identificacionFiscal, client.email,
      client.contactoPrincipal, client.activo ? 'ACTIVO' : 'INACTIVO',
    ]);
  }

  async expectClientState(fiscalId: string, active: boolean): Promise<void> {
    await expect(this.rowByFiscalId(fiscalId).getByRole('cell', {
      name: active ? 'ACTIVO' : 'INACTIVO', exact: true,
    })).toBeVisible();
  }
}
