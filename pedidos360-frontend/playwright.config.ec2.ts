import { defineConfig, devices } from '@playwright/test';

// Config temporal para EV-CN (ISY1102): corre los mismos specs de e2e/ contra
// el sitio real ya desplegado en EC2, sin depender de levantar el stack local.
export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  expect: {
    timeout: 8000,
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report-ec2', open: 'never' }]],
  use: {
    baseURL: 'https://34-227-113-89.sslip.io',
    trace: 'on-first-retry',
    screenshot: 'on',
    video: 'on-first-retry',
    ignoreHTTPSErrors: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
