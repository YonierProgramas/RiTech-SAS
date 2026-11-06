import { test, expect, type Page } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage';
import { DashboardPage } from '../pages/DashboardPage';
import { getRiTechCredentials } from './credentials';
import { ROLE_PROFILES, type RiTechRole } from './roles';

export type AuthenticatedSession = { id: number; nombre: string; email: string; rol: string };

/** Same real UI login for test fixtures and isolated prerequisite contexts; no storageState. */
export async function signInByUi(page: Page, role: RiTechRole): Promise<AuthenticatedSession> {
  return test.step(`Login por UI: ${ROLE_PROFILES[role].role}`, async () => {
    const login = new LoginPage(page);
    const dashboard = new DashboardPage(page);
    const credentials = getRiTechCredentials(role);
    await login.goto();
    const response = await login.login(credentials.username, credentials.password);
    expect(response.status()).toBe(302);
    const location = response.headers()['location'];
    expect(location).toBeTruthy();
    const target = new URL(location, response.url());
    expect(target.origin).toBe(new URL(response.url()).origin);
    expect(target.pathname).toBe('/');
    expect(target.searchParams.has('error')).toBe(false);
    await dashboard.expectLoaded({ rol: ROLE_PROFILES[role].role });
    await expect(page).toHaveURL(url => url.pathname === '/');
    await login.expectHidden();
    const sessionResponse = await page.context().request.get('/api/session', { maxRedirects: 0 });
    expect(sessionResponse.status()).toBe(200);
    expect(sessionResponse.headers()['content-type']).toContain('application/json');
    const session = await sessionResponse.json() as AuthenticatedSession;
    expect(session.id).toBeGreaterThan(0);
    expect(session.email.toLowerCase()).toBe(credentials.username.toLowerCase());
    expect(session.rol).toBe(ROLE_PROFILES[role].role);
    expect(session.nombre.trim().length).toBeGreaterThan(0);
    await dashboard.expectAuthenticated({ nombre: session.nombre, rol: session.rol });
    return session;
  });
}
