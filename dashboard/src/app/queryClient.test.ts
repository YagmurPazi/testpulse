import { ApiError } from '../api/client';
import { shouldRetry } from './queryClient';

describe('shouldRetry', () => {
  it('never retries a client error', () => {
    expect(shouldRetry(0, new ApiError(404, {}))).toBe(false);
    expect(shouldRetry(0, new ApiError(409, {}))).toBe(false);
  });

  it('retries server and network errors twice', () => {
    for (const error of [new ApiError(503, {}), new ApiError(0, {}), new Error('boom')]) {
      expect(shouldRetry(0, error)).toBe(true);
      expect(shouldRetry(1, error)).toBe(true);
      expect(shouldRetry(2, error)).toBe(false);
    }
  });
});
