import { http, HttpResponse } from 'msw/http';
import { problem, server } from '../test/server';
import { ApiError, getJson, postJson } from './client';

describe('getJson', () => {
  it('returns the parsed body of a successful response', async () => {
    server.use(http.get('/api/v1/ping', () => HttpResponse.json({ pong: true })));

    await expect(getJson('/api/v1/ping')).resolves.toEqual({ pong: true });
  });

  it('turns problem details into an ApiError carrying them', async () => {
    server.use(http.get('/api/v1/tests/42', () => problem(404, 'Test not found', 'No test with id 42')));

    const error = await getJson('/api/v1/tests/42').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 404,
      message: 'No test with id 42',
      isClientError: true,
      problem: { title: 'Test not found', detail: 'No test with id 42' },
    });
  });

  it('falls back to the status text when a proxy answers with HTML', async () => {
    server.use(
      http.get(
        '/api/v1/repositories',
        () =>
          new HttpResponse('<html>Bad Gateway</html>', {
            status: 502,
            statusText: 'Bad Gateway',
            headers: { 'Content-Type': 'text/html' },
          }),
      ),
    );

    const error = await getJson('/api/v1/repositories').catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 502, message: 'Bad Gateway', isClientError: false });
  });

  it('reports an unreachable service as status 0', async () => {
    server.use(http.get('/api/v1/repositories', () => HttpResponse.error()));

    const error = await getJson('/api/v1/repositories').catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 0, message: 'The analysis service is not reachable.' });
  });
});

describe('postJson', () => {
  it('sends a POST and returns the body', async () => {
    server.use(http.post('/api/v1/tests/42/quarantine', () => HttpResponse.json({ to: 'QUARANTINED' })));

    await expect(postJson('/api/v1/tests/42/quarantine')).resolves.toEqual({ to: 'QUARANTINED' });
  });

  it('keeps extra problem members such as the current status of a 409', async () => {
    server.use(
      http.post('/api/v1/tests/42/quarantine', () =>
        problem(409, 'Status change not allowed', 'Cannot quarantine a stable test', {
          currentStatus: 'STABLE',
        }),
      ),
    );

    const error = await postJson('/api/v1/tests/42/quarantine').catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 409, problem: { currentStatus: 'STABLE' } });
  });
});
