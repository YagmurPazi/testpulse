import { repositoryPath } from './queries';

describe('repositoryPath', () => {
  it('maps owner/name to path segments', () => {
    expect(repositoryPath('acme/checkout')).toBe('/api/v1/repositories/acme/checkout');
  });

  it('encodes each segment but keeps the separator', () => {
    expect(repositoryPath('my org/a#b')).toBe('/api/v1/repositories/my%20org/a%23b');
  });
});
