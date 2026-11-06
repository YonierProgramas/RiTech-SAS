import { expect, type Locator, type Page } from '@playwright/test';

export const MODULE_LABELS = {
  dashboard: 'Dashboard operativo',
  clientes: 'Clientes internacionales',
  inventario: 'Productos y lotes',
  pedidos: 'Pedidos de exportación',
  exportaciones: 'Expediente y documentación',
  seguimiento: 'Seguimiento de exportación',
  calidad: 'Inspecciones de calidad',
  trazabilidad: 'Trazabilidad de lotes',
} as const;

export type RiTechModule = keyof typeof MODULE_LABELS;
export type ExpectedUser = { nombre?: string; rol?: string };

const MODULE_HEADINGS: Record<RiTechModule, string> = {
  dashboard: 'Dashboard operativo',
  clientes: 'Gestión de clientes',
  inventario: 'Productos y lotes',
  pedidos: 'Pedidos de exportación',
  exportaciones: 'Expedientes de exportación',
  seguimiento: 'Seguimiento de exportación',
  calidad: 'Inspecciones de calidad',
  trazabilidad: 'Consulta de trazabilidad',
};

/** Shared shell of the real single-page frontend; modules do not have separate URLs. */
export abstract class AppPage {
  readonly navigation: Locator;
  readonly pageTitle: Locator;
  readonly userName: Locator;
  readonly roleLabel: Locator;
  readonly logoutButton: Locator;
  readonly notification: Locator;
  protected readonly content: Locator;

  constructor(protected readonly page: Page) {
    this.navigation = page.getByRole('navigation');
    this.pageTitle = page.locator('#page-title');
    this.userName = page.locator('#user-name');
    this.roleLabel = page.locator('#role-label');
    this.logoutButton = page.getByRole('button', { name: 'Cerrar sesión', exact: true });
    this.notification = page.locator('#toast');
    this.content = page.locator('#content');
  }

  async expectAuthenticated(user: ExpectedUser = {}): Promise<void> {
    await expect(this.page).toHaveTitle('RiTech SAS | Operación');
    await expect(this.navigation).toBeVisible();
    await expect(this.logoutButton).toBeVisible();
    await expect(this.userName).not.toHaveText('Cargando…');
    await expect(this.userName).toHaveText(/\S/);
    await expect(this.roleLabel).toHaveText(/\S/);
    if (user.nombre !== undefined) await expect(this.userName).toHaveText(user.nombre);
    if (user.rol !== undefined) await expect(this.roleLabel).toHaveText(user.rol.replaceAll('_', ' '));
  }

  async openModule(module: RiTechModule): Promise<void> {
    const label = MODULE_LABELS[module];
    const button = this.navigation.getByRole('button', { name: label, exact: true });
    await expect(button, `El menú del usuario actual debe incluir ${label}`).toBeVisible();
    await button.click();
    await expect(this.pageTitle).toHaveText(label);
  }

  async expectMessage(message: string | RegExp): Promise<void> {
    await expect(this.notification).toBeVisible();
    await expect(this.notification).toHaveText(message);
  }

  async expectNavigation(visibleModules: readonly RiTechModule[]): Promise<void> {
    await expect(this.navigation.getByRole('button')).toHaveText(visibleModules.map(key => MODULE_LABELS[key]));
    for (const key of Object.keys(MODULE_LABELS) as RiTechModule[]) {
      if (!visibleModules.includes(key)) {
        await expect(this.navigation.getByRole('button', { name: MODULE_LABELS[key], exact: true })).toHaveCount(0);
      }
    }
  }

  async expectModuleLoaded(module: RiTechModule): Promise<void> {
    await expect(this.pageTitle).toHaveText(MODULE_LABELS[module]);
    await expect(this.content.getByRole('heading', { name: MODULE_HEADINGS[module], exact: true })).toBeVisible();
  }

  async logout(): Promise<void> {
    // init() inserts the real CSRF field into this form before displaying the dashboard.
    await expect(this.page.locator('#logout-form input[type="hidden"]')).toHaveValue(/\S/);
    await Promise.all([
      this.page.waitForURL(/\/login\.html\?logout(?:&|$)/),
      this.logoutButton.click(),
    ]);
  }

  protected tableWithColumn(header: string): Locator {
    return this.content.getByRole('table').filter({
      has: this.page.getByRole('columnheader', { name: header, exact: true }),
    });
  }
}
