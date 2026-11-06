import { expect, type Response } from '@playwright/test';
import { AppPage } from './AppPage';

export type TraceEvent = {
  origen_evento: string; referencia_id: number; tipo_evento: string;
  fecha_evento: string; usuario_id: number; descripcion: string;
};
export type TraceView = { codigoLote: string; eventos: TraceEvent[] };

export class TrazabilidadPage extends AppPage {
  readonly heading = this.content.getByRole('heading', { name: 'Consulta de trazabilidad', exact: true });
  readonly form = this.page.locator('#trace-form');
  // The real input has no label; it is the only textbox inside this existing form.
  readonly lotCode = this.form.getByRole('textbox');
  readonly searchButton = this.form.getByRole('button', { name: 'Consultar', exact: true });
  readonly result = this.page.locator('#trace-result');
  readonly eventsTable = this.result.getByRole('table');
  readonly eventRows = this.eventsTable.getByRole('row').filter({ has: this.page.getByRole('cell') });

  async open(): Promise<void> {
    await this.openModule('trazabilidad');
    await this.expectLoaded();
  }

  async expectLoaded(): Promise<void> {
    await expect(this.heading).toBeVisible();
    await expect(this.lotCode).toBeEditable();
    await expect(this.searchButton).toBeVisible();
  }

  async search(code: string): Promise<void> {
    await this.lotCode.fill(code);
    await this.searchButton.click();
  }

  async searchAndWaitForResponse(code: string): Promise<Response> {
    const path = `/api/trazabilidad/${encodeURIComponent(code)}`;
    const [response] = await Promise.all([
      this.page.waitForResponse(r => new URL(r.url()).pathname === path && r.request().method() === 'GET'),
      this.search(code),
    ]);
    return response;
  }

  async expectInitialState(): Promise<void> {
    await this.expectLoaded();
    await expect(this.lotCode).toHaveAttribute('placeholder', 'Código del lote');
    await expect(this.lotCode).toHaveAttribute('required');
    await expect(this.lotCode).toHaveValue('DEMO-LOTE-001');
    await this.expectNoResults();
  }

  async expectNoResults(): Promise<void> {
    await expect(this.result).toBeEmpty();
    await expect(this.eventsTable).toHaveCount(0);
  }

  async expectEmptySearchBlocked(): Promise<void> {
    await expect(this.lotCode).toHaveValue('');
    await expect(this.lotCode).toBeFocused();
    expect(await this.lotCode.evaluate((input: HTMLInputElement) => input.validity.valueMissing)).toBe(true);
  }

  async expectError(message: string): Promise<void> {
    await this.expectMessage(message);
    await expect(this.notification).toHaveClass(/(?:^|\s)error(?:\s|$)/);
  }

  async expectResult(code: string): Promise<void> {
    await expect(this.result.getByRole('heading', { name: code, exact: true })).toBeVisible();
    await expect(this.eventsTable).toBeVisible();
  }

  async expectEventsPresent(): Promise<void> { await expect(this.eventRows).not.toHaveCount(0); }

  async expectTrace(view: TraceView): Promise<void> {
    await this.expectResult(view.codigoLote);
    await expect(this.eventsTable.getByRole('columnheader')).toHaveText(['Fecha', 'Origen', 'Evento', 'Descripción']);
    await expect(this.eventRows).toHaveCount(view.eventos.length);
    const dates = await this.page.evaluate(values => values.map(value => new Date(value).toLocaleString()),
      view.eventos.map(event => event.fecha_evento));
    await expect(this.eventRows.getByRole('cell')).toHaveText(view.eventos.flatMap((event, index) => [
      dates[index], event.origen_evento, event.tipo_evento, event.descripcion,
    ]));
  }

  async expectEventDescription(description: string): Promise<void> {
    await expect(this.eventsTable).toBeVisible();
    await expect(this.eventsTable.getByRole('cell', { name: description, exact: true })).not.toHaveCount(0);
  }
}
