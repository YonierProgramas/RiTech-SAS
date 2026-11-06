import { expect, type Locator, type Response } from '@playwright/test';
import { AppPage, type ExpectedUser } from './AppPage';

export class DashboardPage extends AppPage {
  readonly heading: Locator = this.content.getByRole('heading', { name: 'Dashboard operativo', exact: true });
  readonly activeSession: Locator = this.content.getByText('Sesión activa', { exact: true });
  readonly exportStatesTable: Locator = this.tableWithColumn('Total');

  async goto(): Promise<Response | null> {
    return this.page.goto('/', { waitUntil: 'domcontentloaded' });
  }

  async open(): Promise<void> {
    await this.openModule('dashboard');
    await this.expectLoaded();
  }

  async expectLoaded(user: ExpectedUser = {}): Promise<void> {
    await this.expectAuthenticated(user);
    await expect(this.heading).toBeVisible();
    await expect(this.activeSession).toBeVisible();
    await expect(this.exportStatesTable).toBeVisible();
    for (const label of ['Clientes activos', 'Productos', 'Lotes disponibles', 'Stock disponible kg',
      'Pedidos confirmados', 'Inspecciones aprobadas']) {
      await expect(this.content.getByText(label, { exact: true })).toBeVisible();
    }
  }
}
