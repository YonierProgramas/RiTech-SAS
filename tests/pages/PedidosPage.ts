import { expect, type Locator, type Response } from '@playwright/test';
import { AppPage } from './AppPage';

export type PedidoFormData = {
  clienteId: string | number;
  paisDestino: string;
  loteId: string | number;
  cantidadKg: string | number;
  observaciones?: string;
};

export class PedidosPage extends AppPage {
  readonly heading = this.content.getByRole('heading', { name: 'Pedidos de exportación', exact: true });
  readonly form = this.page.locator('#pedido-form');
  readonly cliente = this.form.getByRole('combobox', { name: 'Cliente', exact: true });
  readonly paisDestino = this.form.getByLabel('País destino', { exact: true });
  readonly lote = this.form.getByRole('combobox', { name: 'Lote', exact: true });
  readonly cantidadKg = this.form.getByLabel('Cantidad kg', { exact: true });
  readonly observaciones = this.form.getByLabel('Observaciones', { exact: true });
  readonly createButton = this.form.getByRole('button', { name: 'Crear pedido', exact: true });
  readonly table = this.tableWithColumn('Cantidad kg');
  readonly rows = this.table.getByRole('row').filter({ has: this.page.getByRole('cell') });

  async open(): Promise<void> {
    await this.openModule('pedidos');
    await this.expectLoaded();
  }

  async expectLoaded(): Promise<void> {
    await expect(this.heading).toBeVisible();
    await expect(this.form).toBeVisible();
    await expect(this.table).toBeVisible();
  }

  async fillForm(data: PedidoFormData): Promise<void> {
    await this.cliente.selectOption({ value: String(data.clienteId) });
    await this.paisDestino.fill(data.paisDestino);
    await this.lote.selectOption({ value: String(data.loteId) });
    await this.cantidadKg.fill(String(data.cantidadKg));
    await this.observaciones.fill(data.observaciones ?? '');
  }

  async create(): Promise<void> { await this.createButton.click(); }

  async createAndWaitForResponse(): Promise<Response> {
    const [response] = await Promise.all([
      this.page.waitForResponse(r => new URL(r.url()).pathname === '/api/pedidos' && r.request().method() === 'POST'),
      this.create(),
    ]);
    return response;
  }

  async expectCreationControls(): Promise<void> {
    await expect(this.cliente).toBeEnabled();
    await expect(this.lote).toBeEnabled();
    await expect(this.paisDestino).toBeEditable();
    await expect(this.cantidadKg).toBeEditable();
    await expect(this.observaciones).toBeEditable();
    await expect(this.createButton).toBeVisible();
    await expect(this.paisDestino).toHaveAttribute('maxlength', '2');
    await expect(this.cantidadKg).toHaveAttribute('min', '0.001');
    await expect(this.cantidadKg).toHaveAttribute('step', '0.001');
  }

  async expectListHeaders(): Promise<void> {
    await expect(this.table.getByRole('columnheader')).toHaveText([
      'Código', 'Cliente', 'Destino', 'Lote', 'Cantidad kg', 'Estado', 'Fecha',
    ]);
  }

  async expectOptions(clients: { id: number; razonSocial: string; activo: boolean }[],
    lots: { id: number; codigo: string; estado: string; cantidadDisponibleKg: number }[]): Promise<void> {
    const activeClients = clients.filter(c => c.activo);
    const activeLots = lots.filter(l => l.estado === 'ACTIVO');
    await expect(this.cliente.getByRole('option')).toHaveText(activeClients.map(c => c.razonSocial));
    expect(await this.cliente.getByRole('option').evaluateAll(options => options.map(o => (o as HTMLOptionElement).value)))
      .toEqual(activeClients.map(c => String(c.id)));
    await expect(this.lote.getByRole('option')).toHaveText(activeLots.map(l => `${l.codigo} · disp. ${l.cantidadDisponibleKg} kg`));
    expect(await this.lote.getByRole('option').evaluateAll(options => options.map(o => (o as HTMLOptionElement).value)))
      .toEqual(activeLots.map(l => String(l.id)));
  }

  async expectMissingRequired(field: 'paisDestino' | 'cantidadKg'): Promise<void> {
    const input = this[field];
    await expect(input).toHaveValue('');
    await expect(input).toBeFocused();
    expect(await input.evaluate((element: HTMLInputElement) => element.validity.valueMissing)).toBe(true);
  }

  async expectQuantityUnderflow(): Promise<void> {
    await expect(this.cantidadKg).toBeFocused();
    expect(await this.cantidadKg.evaluate((element: HTMLInputElement) => element.validity.rangeUnderflow)).toBe(true);
  }

  async expectSuccess(): Promise<void> {
    await this.expectMessage('Pedido creado y lote reservado');
    await expect(this.notification).not.toHaveClass(/(?:^|\s)error(?:\s|$)/);
  }

  async expectError(message: string | RegExp): Promise<void> {
    await this.expectMessage(message);
    await expect(this.notification).toHaveClass(/(?:^|\s)error(?:\s|$)/);
  }

  async reloadList(): Promise<void> {
    await this.page.reload({ waitUntil: 'domcontentloaded' });
    await this.expectModuleLoaded('dashboard');
    await this.open();
  }

  async expectOrderSummary(order: {
    codigo: string; cliente: string; paisDestino: string; lote: string | null;
    cantidadKg: number | null; estado: string; fechaPedido: string;
  }): Promise<void> {
    const row = this.summaryByCode(order.codigo);
    await expect(row).toBeVisible();
    // Format in the browser's locale/timezone, exactly as the audited frontend does.
    const date = await this.page.evaluate(value => new Date(value).toLocaleString(), order.fechaPedido);
    await expect(row.getByRole('cell')).toHaveText([
      order.codigo, order.cliente, order.paisDestino, order.lote ?? '-',
      String(order.cantidadKg ?? '-'), order.estado, date,
    ]);
  }

  summaryByCode(code: string): Locator {
    return this.table.getByRole('row').filter({
      has: this.page.getByRole('cell', { name: code, exact: true }),
    });
  }

  async expectOrderListed(code: string): Promise<void> {
    await expect(this.summaryByCode(code)).toBeVisible();
  }
}
