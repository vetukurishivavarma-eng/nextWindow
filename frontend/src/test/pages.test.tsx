import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../hooks/useAuth';
import { ToastProvider } from '../components/Toast';
import { LoginPage, RegisterPage } from '../pages/AuthPages';
import { WatchCard } from '../components/WatchCard';
import type { Watch } from '../types/api';

afterEach(() => vi.restoreAllMocks());

function renderWithProviders(ui: React.ReactNode) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url.endsWith('/api/auth/me')) return new Response(JSON.stringify({ code: 'UNAUTHENTICATED', message: 'Please sign in.' }), { status: 401 });
    if (url.endsWith('/api/config')) return new Response(JSON.stringify({ demoEnabled: false, pushConfigured: false, vapidPublicKey: null, intervalOptions: [60], defaultIntervalMinutes: 60 }));
    if (url.endsWith('/api/auth/login')) return new Response(JSON.stringify({ code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect.' }), { status: 401 });
    return new Response('{}');
  });
  return render(<MemoryRouter><AuthProvider><ToastProvider>{ui}</ToastProvider></AuthProvider></MemoryRouter>);
}

describe('auth pages', () => {
  it('validates the registration form before submitting', async () => {
    renderWithProviders(<RegisterPage />);
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email');
    await userEvent.type(screen.getByLabelText('Password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Use at least 8 characters')).toBeInTheDocument();
  });

  it('shows the server error on failed login', async () => {
    renderWithProviders(<LoginPage />);
    await userEvent.type(screen.getByLabelText('Email'), 'a@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-pass-1');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
  });
});

describe('WatchCard', () => {
  const watch: Watch = {
    id: 3, title: 'Desk Lamp', watchType: 'PRICE_BELOW', targetPrice: 30, targetKeyword: null, active: true, alertMode: 'REPEAT',
    cooldownMinutes: 60, checkIntervalMinutes: 60, expiresAt: null, completedAt: null, createdAt: '2026-01-01T00:00:00Z',
    lastTriggeredAt: null, lastEvaluatedAt: null, conditionState: 'Above target', conditionMet: false, unreadAlerts: 2,
    source: { id: 1, url: 'https://shop.example.com/lamp', host: 'shop.example.com', sourceType: 'WEB_PAGE', status: 'FAILING',
      statusMessage: 'Timeout', lastFetchedAt: null, lastSuccessAt: null, nextFetchAt: '2026-01-01T01:00:00Z', failureCount: 5, checkIntervalMinutes: 60 },
    current: { title: 'Desk Lamp', price: 39.5, currency: 'USD', priceSource: 'STRUCTURED_DATA', availability: 'IN_STOCK', capturedAt: '2026-01-01T00:00:00Z' },
    previous: null,
  };

  it('shows price, condition, health and unread count', () => {
    render(<MemoryRouter><WatchCard watch={watch} /></MemoryRouter>);
    expect(screen.getByText('Desk Lamp')).toBeInTheDocument();
    expect(screen.getByText(/39\.50/)).toBeInTheDocument();
    expect(screen.getByText('In stock')).toBeInTheDocument();
    expect(screen.getByText('Failing')).toBeInTheDocument();
    expect(screen.getByLabelText('2 unread alerts')).toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute('href', '/watches/3');
  });
});
