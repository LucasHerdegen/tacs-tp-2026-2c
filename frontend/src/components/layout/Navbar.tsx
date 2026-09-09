import React from 'react';
import { Link } from 'react-router-dom';

export const Navbar: React.FC = () => {
  return (
    <header className="bg-indigo-600 text-white shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16 items-center">
          <div className="flex-shrink-0 flex items-center">
            <Link to="/" className="text-xl font-bold tracking-tight">⛅ 404 Sol not found</Link>
          </div>
          <nav className="flex space-x-4">
            <Link to="/activities" className="hover:bg-indigo-500 px-3 py-2 rounded-md text-sm font-medium transition-colors">
              Buscar Actividades
            </Link>
            <button className="hover:bg-indigo-500 px-3 py-2 rounded-md text-sm font-medium transition-colors">
              Mis Actividades
            </button>
          </nav>
        </div>
      </div>
    </header>
  );
};
