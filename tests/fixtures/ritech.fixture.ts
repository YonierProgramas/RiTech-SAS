import { test as base } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage';
import { DashboardPage } from '../pages/DashboardPage';
import { ClientesPage } from '../pages/ClientesPage';
import { PedidosPage } from '../pages/PedidosPage';
import { CalidadPage } from '../pages/CalidadPage';
import { TrazabilidadPage } from '../pages/TrazabilidadPage';
import type { RiTechRole } from '../helpers/roles';
import { signInByUi, type AuthenticatedSession } from '../helpers/ui-session';

type RiTechFixtures = {
  loginPage: LoginPage;
  dashboardPage: DashboardPage;
  clientesPage: ClientesPage;
  pedidosPage: PedidosPage;
  calidadPage: CalidadPage;
  trazabilidadPage: TrazabilidadPage;
  signInAs: (role: RiTechRole) => Promise<AuthenticatedSession>;
};

// Objects are test-scoped and lazy. There is no automatic login or shared session.
export const test = base.extend<RiTechFixtures>({
  loginPage: async ({ page }, use) => { await use(new LoginPage(page)); },
  dashboardPage: async ({ page }, use) => { await use(new DashboardPage(page)); },
  clientesPage: async ({ page }, use) => { await use(new ClientesPage(page)); },
  pedidosPage: async ({ page }, use) => { await use(new PedidosPage(page)); },
  calidadPage: async ({ page }, use) => { await use(new CalidadPage(page)); },
  trazabilidadPage: async ({ page }, use) => { await use(new TrazabilidadPage(page)); },
  signInAs: async ({ page }, use) => { await use(role => signInByUi(page, role)); },
});

export { expect } from '@playwright/test';
