import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { loadEnvFile } from 'node:process';

/** Optional local configuration; injected environment variables keep precedence. */
export function loadLocalE2EEnvironment(): void {
  const file = resolve(__dirname, '../..', '.env.local');
  if (existsSync(file)) loadEnvFile(file);
}
