import type { RepositorySummary } from '../../api/types';

/** Repositories that need attention first: most flaky, then most suspicious tests, then by name. */
export function byAttention(a: RepositorySummary, b: RepositorySummary): number {
  return b.flaky - a.flaky || b.suspicious - a.suspicious || a.repository.localeCompare(b.repository);
}
