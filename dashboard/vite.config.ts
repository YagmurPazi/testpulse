/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// In development the Vite server proxies the API, as nginx does in the container image:
// the browser only ever talks to one origin, so the services need no CORS configuration.
const analysisUrl = process.env.ANALYSIS_URL ?? 'http://localhost:8082';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': analysisUrl,
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}', 'tokens/**/*.test.ts'],
    // Dates are rendered in the local time zone; tests pin it so they behave the same on every machine.
    env: { TZ: 'UTC' },
    // The JUnit report feeds TestPulse's own analysis, like the Surefire reports of the services.
    reporters: ['default', ['junit', { outputFile: 'reports/junit/TEST-dashboard.xml' }]],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
      reporter: ['text-summary', 'html', 'lcov'],
      reportsDirectory: 'reports/coverage',
      // A little below the measured values (lines 94.6 %, branches 89.6 %), see docs/testing.md.
      thresholds: { lines: 92, statements: 92, functions: 90, branches: 85 },
    },
  },
});
