import PageLoader from 'components/loading/PageLoader';
import { AUTH_TOKEN_STORAGE_KEY, aiopsApi, AuthUser } from 'api/aiopsApi';
import { UserRole } from 'types/aiops';
import { createContext, PropsWithChildren, useContext, useEffect, useMemo, useState } from 'react';

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  loading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (...roles: UserRole[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const getStoredToken = () => window.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);

export const AuthProvider = ({ children }: PropsWithChildren) => {
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadCurrentUser = async () => {
      const storedToken = getStoredToken();
      if (!storedToken) {
        setUser(null);
        setToken(null);
        setLoading(false);
        return;
      }

      try {
        setToken(storedToken);
        setUser(await aiopsApi.getCurrentUser());
      } catch {
        window.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
        setToken(null);
        setUser(null);
      } finally {
        setLoading(false);
      }
    };

    loadCurrentUser();
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      loading,
      isAuthenticated: Boolean(token && user),
      login: async (email: string, password: string) => {
        const response = await aiopsApi.login(email, password);
        window.localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, response.token);
        setToken(response.token);
        setUser(response.user);
      },
      logout: async () => {
        try {
          await aiopsApi.logout();
        } catch {
          // Ignore logout request errors for MVP and clear local state anyway.
        }
        window.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
        setToken(null);
        setUser(null);
      },
      hasRole: (...roles: UserRole[]) => (user ? roles.includes(user.role) : false),
    }),
    [loading, token, user],
  );

  if (loading) {
    return <PageLoader />;
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider.');
  }
  return context;
};
