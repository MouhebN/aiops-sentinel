import PageLoader from 'components/loading/PageLoader';
import { useAuth } from 'auth/AuthContext';
import { UserRole } from 'types/aiops';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import paths from 'routes/path';

export const RequireAuth = () => {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <PageLoader />;
  }

  if (!isAuthenticated) {
    return <Navigate to={paths.login} replace state={{ from: location }} />;
  }

  return <Outlet />;
};

export const RequireGuest = () => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return <PageLoader />;
  }

  if (isAuthenticated) {
    return <Navigate to={paths.default} replace />;
  }

  return <Outlet />;
};

export const RequireRole = ({ roles }: { roles: UserRole[] }) => {
  const { hasRole, loading } = useAuth();

  if (loading) {
    return <PageLoader />;
  }

  if (!hasRole(...roles)) {
    return <Navigate to={paths.default} replace />;
  }

  return <Outlet />;
};
