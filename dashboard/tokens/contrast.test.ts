import { readFileSync } from 'node:fs';
import path from 'node:path';

const css = readFileSync(path.resolve(import.meta.dirname, '../src/styles.css'), 'utf8');

// jsdom computes no styles, so axe in the page tests cannot check contrast. The end-to-end tests do, but only in
// the light theme and only for colors on the screen at the time; this checks every text color token in both themes.

function tokens(block: string): Map<string, string> {
  return new Map([...block.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6})/gi)].map((m) => [m[1] ?? '', m[2] ?? '']));
}

const rootBlock = /:root\s*{([^}]*)}/.exec(css)?.[1] ?? '';
const darkBlock = /prefers-color-scheme:\s*dark\)\s*{\s*:root\s*{([^}]*)}/.exec(css)?.[1] ?? '';
const light = tokens(rootBlock);
const dark = new Map([...light, ...tokens(darkBlock)]);

function luminance(hex: string): number {
  const [r = 0, g = 0, b = 0] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [high, low] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (high + 0.05) / (low + 0.05);
}

/** Colors used for text. Fill-only colors (passed, skipped) need 3:1 as non-text and are not listed. */
const TEXT = ['text', 'text-muted', 'accent', 'stable', 'suspicious', 'flaky', 'quarantined', 'failed'];
const BACKGROUNDS = ['surface', 'bg'];
const BADGES = ['stable', 'suspicious', 'flaky', 'quarantined'];

describe.each([
  ['light', light],
  ['dark', dark],
])('%s theme', (_theme, palette) => {
  it('defines the tokens this test checks', () => {
    for (const name of [...TEXT, ...BACKGROUNDS]) {
      expect(palette.get(name), name).toMatch(/^#[0-9a-f]{6}$/i);
    }
  });

  it.each(TEXT.flatMap((text) => BACKGROUNDS.map((background) => [text, background])))(
    '--%s on --%s meets WCAG AA (4.5:1)',
    (text, background) => {
      expect(contrast(palette.get(text) ?? '', palette.get(background) ?? '')).toBeGreaterThanOrEqual(4.5);
    },
  );

  it.each(BADGES)('the %s badge meets WCAG AA (4.5:1)', (status) => {
    expect(contrast(palette.get(status) ?? '', palette.get(`${status}-bg`) ?? '')).toBeGreaterThanOrEqual(
      4.5,
    );
  });
});
