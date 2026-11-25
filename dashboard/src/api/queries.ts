import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getJson, postJson } from './client';
import type {
  DailyTrend,
  RepositorySummary,
  StatusChangeResult,
  TestDetails,
  TestStatus,
  TestSummary,
} from './types';

/**
 * Cache keys, hierarchical so that one invalidation reaches everything below it:
 * invalidating ['repositories'] also refreshes every repository's test list and trend.
 */
export const queryKeys = {
  repositories: ['repositories'] as const,
  tests: (repository: string, status: TestStatus | null) =>
    ['repositories', repository, 'tests', status ?? 'ALL'] as const,
  trend: (repository: string, days: number) => ['repositories', repository, 'trend', days] as const,
  test: (testId: string) => ['tests', testId] as const,
};

const API = '/api/v1';

/** `owner/name` → `/api/v1/repositories/owner/name`, each segment encoded. */
export function repositoryPath(repository: string): string {
  return `${API}/repositories/${repository.split('/').map(encodeURIComponent).join('/')}`;
}

export function useRepositories() {
  return useQuery({
    queryKey: queryKeys.repositories,
    queryFn: () => getJson<RepositorySummary[]>(`${API}/repositories`),
  });
}

export function useTests(repository: string, status: TestStatus | null) {
  return useQuery({
    queryKey: queryKeys.tests(repository, status),
    queryFn: () => {
      const query = status ? `?status=${status}` : '';
      return getJson<TestSummary[]>(`${repositoryPath(repository)}/tests${query}`);
    },
  });
}

export function useTrend(repository: string, days: number) {
  return useQuery({
    queryKey: queryKeys.trend(repository, days),
    queryFn: () => getJson<DailyTrend[]>(`${repositoryPath(repository)}/trend?days=${days}`),
  });
}

export function useTestDetails(testId: string) {
  return useQuery({
    queryKey: queryKeys.test(testId),
    queryFn: () => getJson<TestDetails>(`${API}/tests/${encodeURIComponent(testId)}`),
  });
}

export type StatusAction = 'quarantine' | 'release';

/**
 * Quarantines or releases a test. Whatever the outcome, the cached statuses may be stale afterwards
 * (a 409 means someone else changed the test first), so both the test and every list are refetched.
 */
export function useChangeTestStatus(testId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (action: StatusAction) =>
      postJson<StatusChangeResult>(`${API}/tests/${encodeURIComponent(testId)}/${action}`),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.test(testId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.repositories }),
      ]),
  });
}
