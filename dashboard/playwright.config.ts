import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the running stack (`docker compose up`): the dashboard on :3000 and
 * ingestion-service on :8081, where each test uploads its own runs.
 */
export default defineConfig({
  testDir: './e2e',
  // Uploads go through Kafka and the analysis asynchronously; give that time without hiding real hangs.
  timeout: 60_000,
  expect: { timeout: 20_000 },
  fullyParallel: true,
  forbidOnly: Boolean(process.env['CI']),
  // No retries: a test that passes on a retry is flaky, and this project's purpose is to see that.
  retries: 0,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'reports/playwright' }],
    ['junit', { outputFile: 'reports/e2e/TEST-e2e.xml' }],
  ],
  outputDir: 'test-results',
  use: {
    baseURL: process.env['DASHBOARD_URL'] ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    // README screenshots from the demo data; only on request (npm run screenshots).
    { name: 'screenshots', testMatch: 'screenshots.ts', use: { ...devices['Desktop Chrome'] } },
  ],
});
