import { ROLE_PROFILES, type RiTechRole } from './roles';

export type RiTechCredentials = Readonly<{ username: string; password: string }>;

/** Read only when a test explicitly requests credentials; never log their values. */
export function getRiTechCredentials(
  role: RiTechRole = 'exportaciones',
  env: NodeJS.ProcessEnv = process.env,
): RiTechCredentials {
  const usernameVariable = ROLE_PROFILES[role].usernameVariable;
  const username = env[usernameVariable]?.trim();
  const password = env.RITECH_E2E_PASSWORD;
  if (!username || !password) {
    throw new Error(`Configura ${usernameVariable} y RITECH_E2E_PASSWORD para ejecutar pruebas con login.`);
  }
  return Object.freeze({ username, password });
}
