import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../authContext';

export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="flex min-h-64 items-center justify-center" role="status">
        <span className="text-sm font-medium text-gray-500">Cargando sesión…</span>
      </div>
    );
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}

export function GuestOnly() {
  const { status } = useAuth();

  if (status === 'loading') return null;
  if (status === 'authenticated') return <Navigate to="/" replace />;

  return <Outlet />;
}
