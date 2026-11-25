import { expect, test, type Page } from '@playwright/test';

// Not a test of behaviour: renders the README's screenshots from the seeded demo data (docker compose up with the
// seed). Runs only in the "screenshots" project: npm run screenshots.

const OUT = '../docs/images';

test.use({ viewport: { width: 1280, height: 860 }, colorScheme: 'light' });

async function waitForDemo(page: Page): Promise<void> {
  // The seed uploads 26 runs; the analysis is done when acme/payments has its flaky test.
  await expect
    .poll(
      async () => {
        const response = await page.request.get('/api/v1/repositories/acme/payments/tests?status=FLAKY');
        return response.ok() ? ((await response.json()) as unknown[]).length : 0;
      },
      { timeout: 120_000 },
    )
    .toBe(1);
}

test('repositories', async ({ page }) => {
  await waitForDemo(page);
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'acme/payments' })).toBeVisible();
  await page.screenshot({ path: `${OUT}/repositories.png` });
});

test('repository', async ({ page }) => {
  await waitForDemo(page);
  await page.goto('/repositories/acme/payments');
  await expect(page.getByRole('link', { name: 'refundsPartially' })).toBeVisible();
  await page.screenshot({ path: `${OUT}/repository.png`, fullPage: true });
});

test('test', async ({ page }) => {
  await waitForDemo(page);
  await page.goto('/repositories/acme/payments');
  await page.getByRole('link', { name: 'reconcilesLedger' }).click();
  await expect(
    page.getByRole('list', { name: 'Status changes, newest first' }).getByRole('listitem'),
  ).toHaveCount(3);
  await page.screenshot({ path: `${OUT}/test.png`, fullPage: true });
});
