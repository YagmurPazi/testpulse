import { expect, test } from '@playwright/test';
import { expectNoAccessibilityViolations } from './accessibility';
import { FLAKY_TEST, seedFlakyRepository } from './stack';

test('an uploaded flaky test is visible from the overview down to its history', async ({ page, request }) => {
  const repository = await seedFlakyRepository(request, 'browse');

  await test.step('overview lists the repository with one flaky test', async () => {
    await page.goto('/');
    const row = page.getByRole('row', { name: new RegExp(repository) });
    await expect(row).toBeVisible();
    await expect(row.getByRole('cell').nth(1)).toHaveText('1');
    await expectNoAccessibilityViolations(page);
  });

  await test.step('repository page filters the flaky test', async () => {
    await page.getByRole('link', { name: repository }).click();
    await expect(page.getByRole('heading', { level: 1, name: repository })).toBeVisible();
    await page
      .getByRole('navigation', { name: 'Filter tests by status' })
      .getByRole('link', { name: /Flaky/ })
      .click();
    await expect(page).toHaveURL(/status=FLAKY/);
    await expect(page.getByRole('table', { name: /Tests/ }).getByRole('link')).toHaveText([FLAKY_TEST]);
    await expect(page.getByRole('figure')).toContainText('1 failed, 1 passed only after a rerun');
    await expectNoAccessibilityViolations(page);
  });

  await test.step('test page explains why it is flaky', async () => {
    await page.getByRole('link', { name: FLAKY_TEST }).click();
    const history = page.getByRole('list', { name: 'Status changes, newest first' });
    await expect(history.getByRole('listitem')).toHaveCount(2);
    await expect(history.getByRole('listitem').first()).toContainText(
      'Passed only after 1 failed attempt(s) within one run on commit 4a1b2c3',
    );
    await expect(history.getByRole('listitem').last()).toContainText(
      'Passed and failed on the same commit 9f2c4e1',
    );
    await expectNoAccessibilityViolations(page);
  });
});

test('a flaky test can be quarantined and released', async ({ page, request }) => {
  const repository = await seedFlakyRepository(request, 'quarantine');
  await page.goto(`/repositories/${repository}?status=FLAKY`);
  await page.getByRole('link', { name: FLAKY_TEST }).click();
  const status = page.locator('main header').getByText(/^(Flaky|Quarantined|Suspicious)$/);

  await test.step('quarantine after confirming', async () => {
    await page.getByRole('button', { name: 'Quarantine…' }).click();
    await expect(page.getByRole('group')).toContainText('ignore this test’s result');
    await page.getByRole('button', { name: 'Quarantine test' }).click();
    await expect(page.getByRole('button', { name: 'Release…' })).toBeVisible();
  });

  await test.step('the decision is stored, with a manual history entry', async () => {
    await page.reload();
    await expect(status).toHaveText('Quarantined');
    await expect(
      page.getByRole('list', { name: 'Status changes, newest first' }).getByRole('listitem').first(),
    ).toContainText('manual');
  });

  await test.step('release returns it to suspicious', async () => {
    await page.getByRole('button', { name: 'Release…' }).click();
    await page.getByRole('button', { name: 'Release test' }).click();
    await expect(status).toHaveText('Suspicious');
    await expect(page.getByText('Only flaky tests can be quarantined.', { exact: false })).toBeVisible();
  });
});

test('unknown pages and tests are reported, not blank', async ({ page }) => {
  await page.goto('/tests/00000000-0000-0000-0000-000000000000');
  await expect(page.getByRole('heading', { name: 'Test not found' })).toBeVisible();

  await page.goto('/no/such/page');
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
  await expectNoAccessibilityViolations(page);
});
