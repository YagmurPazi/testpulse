import { screen } from '@testing-library/react';
import { FLAKY_TEST_ID } from '../test/fixtures';
import { accessibilityViolations } from '../test/axe';
import { renderApp } from '../test/render';

describe('accessibility', () => {
  it.each([
    ['repositories', '/', 'acme/checkout'],
    ['repository', '/repositories/acme/checkout', 'appliesDiscount'],
    ['test', `/tests/${FLAKY_TEST_ID}`, 'Status history'],
    ['not found', '/no/such/page', 'Page not found'],
  ])('the %s page has no detectable WCAG violations', async (_page, url, loadedText) => {
    const { container } = renderApp(url);
    await screen.findAllByText(loadedText);

    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('the quarantine confirmation has no detectable WCAG violations', async () => {
    const { container, user } = renderApp(`/tests/${FLAKY_TEST_ID}`);
    await user.click(await screen.findByRole('button', { name: 'Quarantine…' }));

    expect(await accessibilityViolations(container)).toEqual([]);
  });
});
