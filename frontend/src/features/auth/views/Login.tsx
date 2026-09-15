import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button } from '../../../components/ui/Button';
import { Card, CardBody } from '../../../components/ui/Card';
import { ApiError } from '../../../lib/api';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { useAuth } from '../authContext';

interface LoginLocationState {
  from?: { pathname?: string };
  registered?: boolean;
}

function getLoginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Usuario o contraseña inválidos.';
    if (error.status >= 500) return 'El servidor tuvo un problema. Intentá nuevamente más tarde.';
    return error.message;
  }

  return 'No pudimos conectarnos con el servidor. Revisá tu conexión e intentá nuevamente.';
}

export function Login() {
  useDocumentTitle('Iniciar sesión');
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = location.state as LoginLocationState | null;
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      await login({ username: username.trim(), password });
      navigate(locationState?.from?.pathname ?? '/', { replace: true });
    } catch (requestError) {
      setError(getLoginErrorMessage(requestError));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="mx-auto max-w-md py-8 sm:py-14">
      <div className="mb-6 text-center">
        <p className="mb-2 text-sm font-semibold uppercase tracking-wider text-indigo-600">
          Bienvenido de nuevo
        </p>
        <h1 className="text-3xl font-bold tracking-tight text-gray-900">Iniciar sesión</h1>
        <p className="mt-2 text-sm text-gray-600">
          Ingresá para organizar actividades y sumarte a nuevos planes.
        </p>
      </div>

      <Card>
        <CardBody>
          {locationState?.registered && (
            <div className="mb-5 rounded-lg bg-green-50 p-3 text-sm text-green-800" role="status">
              Tu cuenta fue creada. Ya podés iniciar sesión.
            </div>
          )}

          {error && (
            <div className="mb-5 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
              {error}
            </div>
          )}

          <form className="space-y-5" onSubmit={handleSubmit}>
            <div>
              <label className="label-text" htmlFor="login-username">Usuario</label>
              <input
                autoComplete="username"
                className="input-field"
                id="login-username"
                minLength={3}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="Tu nombre de usuario"
                required
                value={username}
              />
            </div>

            <div>
              <label className="label-text" htmlFor="login-password">Contraseña</label>
              <input
                autoComplete="current-password"
                className="input-field"
                id="login-password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Tu contraseña"
                required
                type="password"
                value={password}
              />
            </div>

            <Button className="w-full" disabled={isSubmitting} type="submit">
              {isSubmitting ? 'Ingresando…' : 'Ingresar'}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-600">
            ¿Todavía no tenés cuenta?{' '}
            <Link className="font-semibold text-indigo-600 hover:text-indigo-700" to="/register">
              Registrate
            </Link>
          </p>
        </CardBody>
      </Card>
    </section>
  );
}
