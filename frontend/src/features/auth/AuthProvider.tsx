import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authApi } from './authApi';
import { AuthContext, type AuthStatus } from './authContext';
import type { LoginCredentials, StoredSession, User } from './types';

const STORAGE_KEY = 'tacs.auth';

function readStoredSession(): StoredSession | null {
  try {
    const rawSession = localStorage.getItem(STORAGE_KEY);
    if (!rawSession) return null;

    const session = JSON.parse(rawSession) as Partial<StoredSession>;
    if (
      typeof session.token !== 'string' ||
      typeof session.expiresAt !== 'number' ||
      session.expiresAt <= Date.now()
    ) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }

    return session as StoredSession;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<StoredSession | null>(readStoredSession);
  const [status, setStatus] = useState<AuthStatus>(session ? 'loading' : 'anonymous');
  const [user, setUser] = useState<User | null>(null);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setSession(null);
    setUser(null);
    setStatus('anonymous');
  }, []);

  const login = useCallback(async (credentials: LoginCredentials) => {
    const response = await authApi.login(credentials);
    const nextSession: StoredSession = {
      token: response.token,
      expiresAt: Date.now() + response.expiresIn * 1000,
    };
    const authenticatedUser = await authApi.me(nextSession.token);

    localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
    setUser(authenticatedUser);
    setStatus('authenticated');
  }, []);

  useEffect(() => {
    if (!session || user) return;

    let active = true;
    authApi
      .me(session.token)
      .then((authenticatedUser) => {
        if (!active) return;
        setUser(authenticatedUser);
        setStatus('authenticated');
      })
      .catch(() => {
        if (active) logout();
      });

    return () => {
      active = false;
    };
  }, [logout, session, user]);

  useEffect(() => {
    if (!session) return;

    const expirationTimer = window.setTimeout(
      logout,
      Math.max(0, session.expiresAt - Date.now()),
    );

    return () => {
      window.clearTimeout(expirationTimer);
    };
  }, [logout, session]);

  const value = useMemo(
    () => ({ status, user, token: session?.token ?? null, login, logout }),
    [login, logout, session?.token, status, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
