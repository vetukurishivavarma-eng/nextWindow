import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, onUnauthorized } from '../services/api';
import type { ClientConfig, User } from '../types/api';

interface AuthContextValue {
  user: User | null;
  ready: boolean;
  config: ClientConfig | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  clear: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [config, setConfig] = useState<ClientConfig | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([api.me(), api.config()]).then(([me, cfg]) => {
      if (cancelled) return;
      setUser(me.status === 'fulfilled' ? me.value : null);
      if (cfg.status === 'fulfilled') setConfig(cfg.value);
      setReady(true);
    });
    const off = onUnauthorized(() => setUser(null));
    return () => {
      cancelled = true;
      off();
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => setUser(await api.login(email, password)), []);
  const register = useCallback(async (email: string, password: string) => setUser(await api.register(email, password)), []);
  const logout = useCallback(async () => {
    try {
      await api.logout();
    } finally {
      setUser(null);
    }
  }, []);
  const clear = useCallback(() => setUser(null), []);

  const value = useMemo(() => ({ user, ready, config, login, register, logout, clear }), [user, ready, config, login, register, logout, clear]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
