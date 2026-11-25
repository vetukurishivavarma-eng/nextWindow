import type {
  Alert, Analysis, CheckNowResult, CheckRun, ClientConfig, Dashboard, DemoAction, DemoSource, Health, Page,
  PushDevice, PushTestResult, Settings, SimulateResult, Snapshot, User, Watch, WatchInput,
} from '../types/api';

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

type Listener = () => void;
const unauthorizedListeners = new Set<Listener>();
export function onUnauthorized(fn: Listener) {
  unauthorizedListeners.add(fn);
  return () => unauthorizedListeners.delete(fn);
}

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '';

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(BASE + path, {
      method,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(method !== 'GET' ? { 'Content-Type': 'application/json', 'X-Requested-With': 'NextWindow' } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Can’t reach NextWindow. Check your connection and try again.');
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!res.ok) {
    const d = (data ?? {}) as { code?: string; message?: string; fieldErrors?: Record<string, string> };
    if (res.status === 401 && !path.startsWith('/api/auth/')) unauthorizedListeners.forEach((l) => l());
    throw new ApiError(res.status, d.code ?? 'HTTP_' + res.status, d.message ?? defaultMessage(res.status), d.fieldErrors ?? {});
  }
  return data as T;
}

function defaultMessage(status: number) {
  if (status >= 500) return 'The server had a problem. Please try again shortly.';
  if (status === 429) return 'Too many requests. Please wait a moment.';
  if (status === 404) return 'Not found.';
  return 'Request failed.';
}

const qs = (params: Record<string, string | number | boolean | undefined | null>) => {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  });
  const s = p.toString();
  return s ? '?' + s : '';
};

export const api = {
  config: () => request<ClientConfig>('GET', '/api/config'),

  register: (email: string, password: string) => request<User>('POST', '/api/auth/register', { email, password }),
  login: (email: string, password: string) => request<User>('POST', '/api/auth/login', { email, password }),
  logout: () => request<void>('POST', '/api/auth/logout'),
  me: () => request<User>('GET', '/api/auth/me'),

  dashboard: () => request<Dashboard>('GET', '/api/dashboard'),

  analyze: (url: string) => request<Analysis>('POST', '/api/sources/analyze', { url }),
  watches: (params: { filter?: string; q?: string; page?: number; size?: number } = {}) =>
    request<Page<Watch>>('GET', '/api/watches' + qs(params)),
  watch: (id: number) => request<Watch>('GET', `/api/watches/${id}`),
  createWatch: (input: WatchInput) => request<Watch>('POST', '/api/watches', input),
  updateWatch: (id: number, input: WatchInput) => request<Watch>('PUT', `/api/watches/${id}`, input),
  pauseWatch: (id: number) => request<Watch>('POST', `/api/watches/${id}/pause`),
  resumeWatch: (id: number) => request<Watch>('POST', `/api/watches/${id}/resume`),
  deleteWatch: (id: number) => request<void>('DELETE', `/api/watches/${id}`),
  checkNow: (id: number) => request<CheckNowResult>('POST', `/api/watches/${id}/check-now`),
  snapshots: (id: number, page = 0, size = 50) => request<Page<Snapshot>>('GET', `/api/watches/${id}/snapshots` + qs({ page, size })),
  watchChecks: (id: number, page = 0, size = 10) => request<Page<CheckRun>>('GET', `/api/watches/${id}/checks` + qs({ page, size })),

  alerts: (params: { unread?: boolean; watchId?: number; page?: number; size?: number } = {}) =>
    request<Page<Alert>>('GET', '/api/alerts' + qs(params)),
  unreadCount: () => request<{ unread: number }>('GET', '/api/alerts/unread-count'),
  markRead: (id: number) => request<Alert>('POST', `/api/alerts/${id}/read`),
  markUnread: (id: number) => request<Alert>('POST', `/api/alerts/${id}/unread`),
  markAllRead: () => request<{ updated: number }>('POST', '/api/alerts/read-all'),

  health: () => request<Health>('GET', '/api/monitoring/health'),
  checks: (params: { filter?: string; page?: number; size?: number } = {}) =>
    request<Page<CheckRun>>('GET', '/api/monitoring/checks' + qs(params)),

  settings: () => request<Settings>('GET', '/api/settings'),
  updateSettings: (pushEnabled: boolean) => request<Settings>('PUT', '/api/settings', { pushEnabled }),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('POST', '/api/account/password', { currentPassword, newPassword }),
  deleteAccount: (password: string) => request<void>('DELETE', '/api/account', { password }),

  pushDevices: () => request<PushDevice[]>('GET', '/api/push/subscriptions'),
  pushSubscribe: (sub: PushSubscriptionJSON) =>
    request<PushDevice>('POST', '/api/push/subscribe', { endpoint: sub.endpoint, keys: sub.keys }),
  pushUnsubscribe: (endpoint: string) => request<void>('POST', '/api/push/unsubscribe', { endpoint }),
  pushRemoveDevice: (id: number) => request<void>('DELETE', `/api/push/subscriptions/${id}`),
  pushTest: () => request<PushTestResult>('POST', '/api/push/test'),

  demoSources: () => request<DemoSource[]>('GET', '/api/demo/sources'),
  simulate: (slug: string, action: DemoAction, keyword?: string) =>
    request<SimulateResult>('POST', `/api/demo/sources/${slug}/simulate`, { action, keyword, checkNow: true }),
};
