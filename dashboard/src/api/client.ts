import type { ProblemDetail } from './types';

/** A failed API call. {@link status} is 0 when the service could not be reached at all. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  /** Client errors (4xx) will not go away by retrying the same request. */
  get isClientError(): boolean {
    return this.status >= 400 && this.status < 500;
  }
}

export function getJson<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'GET' });
}

export function postJson<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'POST' });
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  // Resolved against the page's origin: the API is served from the same origin as the dashboard.
  const url = new URL(path, window.location.href);
  let response: Response;
  try {
    response = await fetch(url, { ...init, headers: { Accept: 'application/json' } });
  } catch {
    throw new ApiError(0, { title: 'Network error', detail: 'The analysis service is not reachable.' });
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  return (await response.json()) as T;
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  const fallback: ProblemDetail = { status: response.status, title: response.statusText || 'Request failed' };
  // A proxy in front of the service (nginx) answers 502/504 with HTML, not problem details.
  if (!response.headers.get('Content-Type')?.includes('json')) {
    return fallback;
  }
  try {
    return { ...fallback, ...((await response.json()) as ProblemDetail) };
  } catch {
    return fallback;
  }
}
