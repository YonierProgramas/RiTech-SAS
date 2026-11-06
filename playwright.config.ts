import { defineConfig, devices } from '@playwright/test';
import { loadLocalE2EEnvironment } from './tests/helpers/env';

loadLocalE2EEnvironment();

const isCI = Boolean(process.env.CI);

// RiTech must already be running manually; automated server startup is deferred to CI setup.
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  // Real database fixtures compare business snapshots across modules. Avoid concurrent mutations.
  workers: 1,
  timeout: 30_000,
  expect: { timeout: 5_000 },
  outputDir: './test-results',
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: 'http://localhost:8080',
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
