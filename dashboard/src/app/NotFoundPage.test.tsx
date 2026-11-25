import { screen } from '@testing-library/react';
import { renderApp } from '../test/render';

describe('unknown URLs', () => {
  it('show a not-found page with a way back', async () => {
    const { user } = renderApp('/no/such/page');

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Back to repositories' }));
    expect(screen.queryByRole('heading', { name: 'Page not found' })).not.toBeInTheDocument();
  });

  it('keep the skip link pointing at the main content', () => {
    renderApp('/no/such/page');

    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main');
  });
});
