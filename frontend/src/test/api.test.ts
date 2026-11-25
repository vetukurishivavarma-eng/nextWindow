import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../services/api';

afterEach(() => vi.restoreAllMocks());

describe('api client', () => {
  it('sends the CSRF header and credentials on mutating requests', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ id: 1, email: 'a@b.co' }), { status: 200 }));
    await api.login('a@b.co', 'secret-123');
    const [, init] = fetchMock.mock.calls[0];
    expect((init!.headers as Record<string, string>)['X-Requested-With']).toBe('NextWindow');
    expect(init!.credentials).toBe('include');
  });

  it('turns error responses into ApiError with field errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      status: 400, code: 'VALIDATION_FAILED', message: 'Please correct the highlighted fields.', fieldErrors: { url: 'Enter the URL' },
    }), { status: 400 }));
    const err = await api.createWatch({ watchType: 'ANY_CHANGE', alertMode: 'REPEAT', cooldownMinutes: 0, checkIntervalMinutes: 60 }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('VALIDATION_FAILED');
    expect(err.fieldErrors.url).toBe('Enter the URL');
  });

  it('reports network failures clearly', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    const err = await api.me().catch((e) => e);
    expect(err.code).toBe('NETWORK');
  });
});
