import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../features/auth/authContext';

export const Navbar: React.FC = () => {
  const { status, user, logout } = useAuth();

  return (
    <header className="bg-indigo-600 text-white shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16 items-center">
          <div className="flex-shrink-0 flex items-center">
            <Link to="/" className="text-xl font-bold tracking-tight">⛅ 404 Sol not found</Link>
          </div>
          <nav className="flex items-center gap-2">
            {status === 'authenticated' && (
              <>
                <Link to="/activities" className="hover:bg-indigo-500 px-3 py-2 rounded-md text-sm font-medium transition-colors">
                  Buscar Actividades
                </Link>
                <Link to="/my-activities" className="hover:bg-indigo-500 px-3 py-2 rounded-md text-sm font-medium transition-colors">
                  Mis Actividades
                </Link>
                <span className="hidden border-l border-indigo-400 pl-4 text-sm sm:inline">
                  {user?.username}
                  {user?.rol === 'ADMIN' && (
                    <span className="ml-2 rounded-full bg-indigo-200 px-2 py-0.5 text-xs font-semibold text-indigo-900">
                      Admin
                    </span>
                  )}
                </span>
                <button
                  className="rounded-md border border-indigo-300 px-3 py-2 text-sm font-medium transition-colors hover:bg-indigo-500"
                  onClick={logout}
                  type="button"
                >
                  Salir
                </button>
              </>
            )}

            {status === 'anonymous' && (
              <>
                <Link className="rounded-md px-3 py-2 text-sm font-medium hover:bg-indigo-500" to="/login">
                  Ingresar
                </Link>
                <Link className="rounded-md bg-white px-3 py-2 text-sm font-semibold text-indigo-700 hover:bg-indigo-50" to="/register">
                  Crear cuenta
                </Link>
              </>
            )}
          </nav>
        </div>
      </div>
    </header>
  );
};
