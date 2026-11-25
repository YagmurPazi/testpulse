import type { RepositorySummary } from '../../api/types';
import { byAttention } from './order';

function repo(repository: string, flaky: number, suspicious: number): RepositorySummary {
  return {
    repository,
    tests: 10,
    stable: 10 - flaky - suspicious,
    suspicious,
    flaky,
    quarantined: 0,
    lastRunAt: null,
  };
}

describe('byAttention', () => {
  it('puts flaky before suspicious before healthy, then sorts by name', () => {
    const sorted = [
      repo('d/healthy', 0, 0),
      repo('c/suspicious', 0, 2),
      repo('a/flaky', 1, 0),
      repo('b/flaky', 3, 0),
      repo('a/healthy', 0, 0),
    ].sort(byAttention);

    expect(sorted.map((r) => r.repository)).toEqual([
      'b/flaky',
      'a/flaky',
      'c/suspicious',
      'a/healthy',
      'd/healthy',
    ]);
  });
});
