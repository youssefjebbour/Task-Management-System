import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, post, refreshSession, setAccessToken, setSessionListener } from './api';
import type { Session, User } from './types';
interface Auth {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fields: Record<string, string>) => Promise<void>;
  logout: () => Promise<void>;
  reload: () => Promise<void>;
}
const AuthContext = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const client = useQueryClient();
  useEffect(() => {
    setSessionListener((session) => setUser(session?.user ?? null));
    refreshSession()
      .catch(() => {})
      .finally(() => setLoading(false));
    return () => setSessionListener(() => {});
  }, []);
  const accept = (session: Session) => {
    client.clear();
    setAccessToken(session.accessToken);
    setUser(session.user);
  };
  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        login: async (email, password) =>
          accept(await post<Session>('/auth/login', { email, password })),
        register: async (fields) => accept(await post<Session>('/auth/register', fields)),
        logout: async () => {
          await post('/auth/logout');
          setAccessToken(null);
          setUser(null);
          client.clear();
        },
        reload: async () => setUser(await api<User>('/users/me')),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('AuthProvider missing');
  return value;
}
