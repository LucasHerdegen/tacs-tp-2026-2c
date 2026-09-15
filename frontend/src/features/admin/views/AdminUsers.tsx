import { useEffect, useState } from 'react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card, CardBody } from '../../../components/ui/Card';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { ApiError } from '../../../lib/api';
import { useAuth } from '../../auth/authContext';
import type { User, UserId, UserRole } from '../../auth/types';
import { adminApi } from '../adminApi';

function mensajeDeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'No tenés permisos para gestionar usuarios.';
    return error.message;
  }

  return 'No pudimos conectarnos con el servidor.';
}

export function AdminUsers() {
  useDocumentTitle('Gestión de roles');
  const { token, user: authenticatedUser } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [updatingUserId, setUpdatingUserId] = useState<UserId | null>(null);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  useEffect(() => {
    if (!token) return;
    let active = true;

    adminApi
      .usuarios(token)
      .then((foundUsers) => {
        if (!active) return;
        setUsers([...foundUsers].sort((first, second) => first.username.localeCompare(second.username)));
      })
      .catch((requestError: unknown) => {
        if (active) setError(mensajeDeError(requestError));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [token]);

  async function handleRoleChange(targetUser: User, nextRole: UserRole) {
    if (!token || targetUser.rol === nextRole) return;

    setUpdatingUserId(targetUser.id);
    setError('');
    setSuccessMessage('');

    try {
      const updatedUser = await adminApi.actualizarRol(targetUser.id, nextRole, token);
      setUsers((currentUsers) =>
        currentUsers.map((currentUser) =>
          currentUser.id === updatedUser.id ? updatedUser : currentUser,
        ),
      );
      setSuccessMessage(`El rol de ${updatedUser.username} ahora es ${updatedUser.rol}.`);
    } catch (requestError) {
      setError(mensajeDeError(requestError));
    } finally {
      setUpdatingUserId(null);
    }
  }

  if (loading) {
    return <p className="py-20 text-center text-gray-500">Cargando usuarios…</p>;
  }

  return (
    <section className="space-y-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Administración</p>
        <h1 className="mt-1 text-3xl font-bold text-gray-900">Gestión de roles</h1>
        <p className="mt-2 text-sm text-gray-600">
          Elegí qué usuarios tienen acceso a las funciones administrativas.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="rounded-lg border border-green-200 bg-green-50 p-4 text-sm text-green-800" role="status">
          {successMessage}
        </div>
      )}

      <Card>
        <CardBody className="p-0">
          {users.length === 0 ? (
            <p className="p-6 text-sm text-gray-500">No hay usuarios registrados.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                  <tr>
                    <th className="px-6 py-3 font-semibold">Usuario</th>
                    <th className="px-6 py-3 font-semibold">Rol actual</th>
                    <th className="px-6 py-3 font-semibold">Acción</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {users.map((listedUser) => {
                    const nextRole: UserRole = listedUser.rol === 'ADMIN' ? 'USER' : 'ADMIN';
                    const isUpdating = updatingUserId === listedUser.id;

                    return (
                      <tr key={listedUser.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4 font-medium text-gray-900">
                          {listedUser.username}
                          {listedUser.id === authenticatedUser?.id && (
                            <span className="ml-2 text-xs font-normal text-gray-500">(vos)</span>
                          )}
                        </td>
                        <td className="px-6 py-4">
                          <Badge variant={listedUser.rol === 'ADMIN' ? 'info' : 'neutral'}>
                            {listedUser.rol}
                          </Badge>
                        </td>
                        <td className="px-6 py-4">
                          <Button
                            disabled={isUpdating}
                            onClick={() => handleRoleChange(listedUser, nextRole)}
                            variant={nextRole === 'ADMIN' ? 'primary' : 'secondary'}
                          >
                            {isUpdating
                              ? 'Actualizando…'
                              : nextRole === 'ADMIN'
                                ? 'Convertir en ADMIN'
                                : 'Convertir en USER'}
                          </Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>
    </section>
  );
}
