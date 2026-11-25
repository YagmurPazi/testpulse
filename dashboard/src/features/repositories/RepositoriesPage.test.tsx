import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw/http';
import { renderApp } from '../../test/render';
import { problem, server } from '../../test/server';

describe('repositories page', () => {
  it('lists repositories, most urgent first, with counts per status', async () => {
    renderApp('/');

    await screen.findByRole('link', { name: 'acme/checkout' });

    expect(screen.getAllByRole('rowheader').map((header) => header.textContent)).toEqual([
      'acme/checkout',
      'YagmurPazi/testpulse',
    ]);
    const checkout = screen.getByRole('row', { name: /acme\/checkout/ });
    // Tests, flaky, quarantined, suspicious, stable
    expect(
      within(checkout)
        .getAllByRole('cell')
        .slice(0, 5)
        .map((cell) => cell.textContent),
    ).toEqual(['4', '1', '0', '0', '3']);
  });

  it('links each repository to its page', async () => {
    const { user } = renderApp('/');

    await user.click(await screen.findByRole('link', { name: 'acme/checkout' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'acme/checkout' })).toBeInTheDocument();
  });

  it('explains how to upload reports when nothing has been analysed yet', async () => {
    server.use(http.get('/api/v1/repositories', () => HttpResponse.json([])));

    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'No test runs analysed yet' })).toBeInTheDocument();
    expect(screen.getByText(/api\/v1\/test-runs/)).toBeInTheDocument();
  });

  it('shows the error and retries on request', async () => {
    let calls = 0;
    server.use(
      http.get('/api/v1/repositories', () => {
        calls += 1;
        return calls === 1 ? problem(503, 'Service Unavailable', 'Database is down') : HttpResponse.json([]);
      }),
    );
    const { user } = renderApp('/');

    expect(await screen.findByRole('alert')).toHaveTextContent('Database is down');
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('heading', { name: 'No test runs analysed yet' })).toBeInTheDocument();
  });
});
