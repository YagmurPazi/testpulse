import { http, HttpResponse } from 'msw/http';
import { setupServer } from 'msw/node';
import { checkoutTests, checkoutTrend, FLAKY_TEST_ID, flakyTestDetails, repositories } from './fixtures';

/**
 * The analysis API as the dashboard sees it, at the network level: components and hooks run unchanged
 * and fetch real responses. Tests override single endpoints with `server.use(...)`.
 */
export const handlers = [
  http.get('/api/v1/repositories', () => HttpResponse.json(repositories)),
  http.get('/api/v1/repositories/acme/checkout/tests', ({ request }) => {
    const status = new URL(request.url).searchParams.get('status');
    return HttpResponse.json(status ? checkoutTests.filter((test) => test.status === status) : checkoutTests);
  }),
  http.get('/api/v1/repositories/acme/checkout/trend', () => HttpResponse.json(checkoutTrend)),
  http.get(`/api/v1/tests/${FLAKY_TEST_ID}`, () => HttpResponse.json(flakyTestDetails)),
];

export const server = setupServer(...handlers);

export function problem(status: number, title: string, detail: string, extra: object = {}) {
  return HttpResponse.json(
    { type: 'about:blank', title, status, detail, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}
