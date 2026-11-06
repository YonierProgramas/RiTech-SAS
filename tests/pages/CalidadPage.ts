import { expect, type Locator, type Response } from '@playwright/test';
import { AppPage } from './AppPage';

export type InspeccionFormData = {
  loteId: string | number;
  humedadPorcentaje?: string | number | null;
  evaluacionTaza?: string;
  aroma?: string;
  resultado: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';
  defectos?: string;
  observaciones?: string;
};

export type VisibleInspection = {
  lote: string; inspector: string; fechaInspeccion: string;
  humedadPorcentaje: number | null; resultado: string; observaciones: string | null;
};

export class CalidadPage extends AppPage {
  readonly heading = this.content.getByRole('heading', { name: 'Inspecciones de calidad', exact: true });
  readonly form = this.page.locator('#calidad-form');
  readonly lote = this.form.getByRole('combobox', { name: 'Lote', exact: true });
  readonly humedad = this.form.getByLabel('Humedad %', { exact: true });
  readonly evaluacionTaza = this.form.getByLabel('Evaluación de taza', { exact: true });
  readonly aroma = this.form.getByLabel('Aroma', { exact: true });
  readonly resultado = this.form.getByRole('combobox', { name: 'Resultado', exact: true });
  readonly defectos = this.form.getByLabel('Defectos', { exact: true });
  readonly observaciones = this.form.getByLabel('Observaciones', { exact: true });
  readonly registerButton = this.form.getByRole('button', { name: 'Registrar inspección', exact: true });
  readonly table = this.tableWithColumn('Humedad');
  readonly rows = this.table.getByRole('row').filter({ has: this.page.getByRole('cell') });

  async open(): Promise<void> {
    await this.openModule('calidad');
    await this.expectLoaded();
  }

  async expectLoaded(): Promise<void> {
    await expect(this.heading).toBeVisible();
    await expect(this.form).toBeVisible();
    await expect(this.table).toBeVisible();
  }

  async fillForm(data: InspeccionFormData): Promise<void> {
    await this.lote.selectOption({ value: String(data.loteId) });
    await this.humedad.fill(data.humedadPorcentaje == null ? '' : String(data.humedadPorcentaje));
    await this.evaluacionTaza.fill(data.evaluacionTaza ?? '');
    await this.aroma.fill(data.aroma ?? '');
    await this.resultado.selectOption(data.resultado);
    await this.defectos.fill(data.defectos ?? '');
    await this.observaciones.fill(data.observaciones ?? '');
  }

  async register(): Promise<void> { await this.registerButton.click(); }

  async registerAndWaitForResponse(): Promise<Response> {
    const [response] = await Promise.all([
      this.page.waitForResponse(r => new URL(r.url()).pathname === '/api/calidad' && r.request().method() === 'POST'),
      this.register(),
    ]);
    return response;
  }

  async expectCreationControls(): Promise<void> {
    await expect(this.lote).toBeEnabled();
    await expect(this.resultado).toBeEnabled();
    for (const input of [this.humedad, this.evaluacionTaza, this.aroma, this.defectos, this.observaciones]) {
      await expect(input).toBeEditable();
    }
    await expect(this.registerButton).toBeVisible();
    await expect(this.humedad).toHaveAttribute('min', '0');
    await expect(this.humedad).toHaveAttribute('max', '100');
    await expect(this.humedad).toHaveAttribute('step', '0.01');
    // No controls have required in the audited HTML. Mandatory DTO fields are tested via API.
    for (const input of [this.lote, this.resultado, this.humedad, this.evaluacionTaza, this.aroma, this.defectos, this.observaciones]) {
      await expect(input).not.toHaveAttribute('required');
    }
  }

  async expectListHeaders(): Promise<void> {
    await expect(this.table.getByRole('columnheader')).toHaveText([
      'Lote', 'Inspector', 'Fecha', 'Humedad', 'Resultado', 'Observaciones',
    ]);
  }

  async expectOptions(lots: { id: number; codigo: string; producto: string }[]): Promise<void> {
    await expect(this.lote.getByRole('option')).toHaveText(lots.map(l => `${l.codigo} · ${l.producto}`));
    expect(await this.lote.getByRole('option').evaluateAll(options => options.map(o => (o as HTMLOptionElement).value)))
      .toEqual(lots.map(l => String(l.id)));
    await expect(this.resultado.getByRole('option')).toHaveText(['PENDIENTE', 'APROBADO', 'RECHAZADO']);
  }

  async expectHumidityRangeError(direction: 'underflow' | 'overflow'): Promise<void> {
    await expect(this.humedad).toBeFocused();
    expect(await this.humedad.evaluate((element: HTMLInputElement, side) =>
      side === 'underflow' ? element.validity.rangeUnderflow : element.validity.rangeOverflow, direction)).toBe(true);
  }

  async expectSuccess(): Promise<void> {
    await this.expectMessage('Inspección registrada');
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

  rowsForLot(lotCode: string): Locator {
    return this.rows.filter({ has: this.page.getByRole('cell', { name: lotCode, exact: true }) });
  }

  rowByIdentity(lotCode: string, observations: string): Locator {
    return this.rowsForLot(lotCode).filter({ has: this.page.getByRole('cell', { name: observations, exact: true }) });
  }

  async expectInspectionRows(records: VisibleInspection[], rows: Locator = this.rows): Promise<void> {
    const dates = await this.page.evaluate(values => values.map(value => new Date(value).toLocaleString()),
      records.map(i => i.fechaInspeccion));
    await expect(rows).toHaveCount(records.length);
    await expect(rows.getByRole('cell')).toHaveText(records.flatMap((i, index) => [
      i.lote, i.inspector, dates[index], String(i.humedadPorcentaje ?? '-'), i.resultado, i.observaciones || '-',
    ]));
  }

  async expectResultForLot(lotCode: string, result: InspeccionFormData['resultado']): Promise<void> {
    const row = this.table.getByRole('row').filter({
      has: this.page.getByRole('cell', { name: lotCode, exact: true }),
    }).filter({ has: this.page.getByRole('cell', { name: result, exact: true }) });
    await expect(row).not.toHaveCount(0);
  }
}
