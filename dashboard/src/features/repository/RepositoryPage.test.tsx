import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw/http';
import { renderApp } from '../../test/render';
import { server } from '../../test/server';

function testRows() {
  const table = screen.getByRole('table', { name: 'Tests, most severe status first' });
  return within(table).getAllByRole('row').slice(1);
}

describe('repository page', () => {
  it('lists the tests with status, latest reason and counts', async () => {
    renderApp('/repositories/acme/checkout');

    expect(await screen.findByRole('link', { name: 'appliesDiscount' })).toHaveAttribute(
      'href',
      '/tests/ce1cbd14-a6b1-4100-8d48-2441ee64eb6f',
    );
    const [flaky, stable] = testRows();
    expect(flaky).toHaveTextContent('Flaky');
    expect(flaky).toHaveTextContent('Passed only after 1 failed attempt(s) within one run on commit 4a1b2c3');
    expect(stable).toHaveTextContent('calculatesTotal');
    expect(stable).toHaveTextContent('Stable');
  });

  it('filters by status through the URL and shows the counts per status', async () => {
    const { user } = renderApp('/repositories/acme/checkout');
    const filter = screen.getByRole('navigation', { name: 'Filter tests by status' });

    await user.click(await within(filter).findByRole('link', { name: /Flaky\s*1/ }));

    await waitFor(() => {
      expect(testRows()).toHaveLength(1);
    });
    expect(within(filter).getByRole('link', { name: /Flaky/ })).toHaveAttribute('aria-current', 'page');
    expect(within(filter).getByRole('link', { name: /All\s*4/ })).not.toHaveAttribute('aria-current');
  });

  it('reads the filter from a shared link', async () => {
    renderApp('/repositories/acme/checkout?status=STABLE');

    expect(await screen.findByRole('link', { name: 'calculatesTotal' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'appliesDiscount' })).not.toBeInTheDocument();
  });

  it('says when no test has the selected status', async () => {
    renderApp('/repositories/acme/checkout?status=QUARANTINED');

    expect(await screen.findByText('No quarantined tests.')).toBeInTheDocument();
  });

  it('summarises the trend and offers it as a table', async () => {
    const { user } = renderApp('/repositories/acme/checkout');

    const figure = await screen.findByRole('figure');
    expect(figure).toHaveTextContent(
      'Last 30 days: 16 executions in 4 runs, 1 failed, 1 passed only after a rerun.',
    );
    await user.click(within(figure).getByText('Show as table'));
    expect(within(figure).getByRole('rowheader', { name: '25 Nov' })).toBeInTheDocument();
  });

  it('requests another time range', async () => {
    const requested: (string | null)[] = [];
    server.use(
      http.get('/api/v1/repositories/acme/checkout/trend', ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('days'));
        return HttpResponse.json([]);
      }),
    );
    const { user } = renderApp('/repositories/acme/checkout');

    await user.click(await screen.findByRole('button', { name: '7 days' }));

    expect(await screen.findByRole('button', { name: '7 days', pressed: true })).toBeInTheDocument();
    await waitFor(() => {
      expect(requested).toEqual(['30', '7']);
    });
  });

  it('reports a repository TestPulse does not know', async () => {
    server.use(
      http.get('/api/v1/repositories/acme/unknown/tests', () => HttpResponse.json([])),
      http.get('/api/v1/repositories/acme/unknown/trend', () => HttpResponse.json([])),
    );

    renderApp('/repositories/acme/unknown');

    expect(await screen.findByRole('heading', { name: 'Repository not found' })).toBeInTheDocument();
  });
});

describe('trend chart', () => {
  it('is hidden from assistive technology and not focusable, as its data is in the caption and table', async () => {
    renderApp('/repositories/acme/checkout');

    const figure = await screen.findByRole('figure');
    const chart = figure.querySelector('.recharts-wrapper > svg');
    expect(chart).toHaveAttribute('aria-hidden', 'true');
    expect(chart).not.toHaveAttribute('tabindex');
  });
});
