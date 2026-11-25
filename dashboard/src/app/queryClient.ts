import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/client';

const MAX_RETRIES = 2;

/** Retries server and network errors, but never a 4xx: the same request would fail the same way. */
export function shouldRetry(failureCount: number, error: Error): boolean {
  if (error instanceof ApiError && error.isClientError) {
    return false;
  }
  return failureCount < MAX_RETRIES;
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Statuses change when a CI run is analysed, not every second: refetch after 15 s at the earliest.
        staleTime: 15_000,
        retry: shouldRetry,
      },
    },
  });
}
