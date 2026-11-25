import axe from 'axe-core';

/**
 * WCAG A/AA violations axe finds in the rendered DOM. Color contrast is left to the end-to-end tests:
 * jsdom does not compute styles, so axe cannot judge colors here.
 */
export async function accessibilityViolations(container: Element): Promise<string[]> {
  const results = await axe.run(container, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] },
    rules: { 'color-contrast': { enabled: false } },
  });
  return results.violations.map((violation) => `${violation.id}: ${violation.help}`);
}
