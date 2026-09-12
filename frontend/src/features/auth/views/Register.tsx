import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '../../../components/ui/Button';
import { Card, CardBody } from '../../../components/ui/Card';
import { ApiError } from '../../../lib/api';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { authApi } from '../authApi';

export function Register() {
  useDocumentTitle('Crear cuenta');
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirmation, setPasswordConfirmation] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');

    if (password !== passwordConfirmation) {
      setError('Las contraseñas no coinciden.');
      return;
    }

    setIsSubmitting(true);
    try {
      await authApi.register({ username: username.trim(), password });
      navigate('/login', { replace: true, state: { registered: true } });
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : 'No pudimos crear la cuenta. Intentá nuevamente.',
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="mx-auto max-w-md py-8 sm:py-14">
      <div className="mb-6 text-center">
        <p className="mb-2 text-sm font-semibold uppercase tracking-wider text-indigo-600">
          Sumate a la comunidad
        </p>
        <h1 className="text-3xl font-bold tracking-tight text-gray-900">Crear cuenta</h1>
        <p className="mt-2 text-sm text-gray-600">
          Elegí tus datos de acceso para empezar a participar.
        </p>
      </div>

      <Card>
        <CardBody>
          {error && (
            <div className="mb-5 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
              {error}
            </div>
          )}

          <form className="space-y-5" onSubmit={handleSubmit}>
            <div>
              <label className="label-text" htmlFor="register-username">Usuario</label>
              <input
                autoComplete="username"
                className="input-field"
                id="register-username"
                maxLength={50}
                minLength={3}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="Entre 3 y 50 caracteres"
                required
                value={username}
              />
            </div>

            <div>
              <label className="label-text" htmlFor="register-password">Contraseña</label>
              <input
                autoComplete="new-password"
                className="input-field"
                id="register-password"
                maxLength={72}
                minLength={8}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Entre 8 y 72 caracteres"
                required
                type="password"
                value={password}
              />
            </div>

            <div>
              <label className="label-text" htmlFor="register-password-confirmation">
                Repetir contraseña
              </label>
              <input
                autoComplete="new-password"
                className="input-field"
                id="register-password-confirmation"
                maxLength={72}
                minLength={8}
                onChange={(event) => setPasswordConfirmation(event.target.value)}
                placeholder="Volvé a escribir tu contraseña"
                required
                type="password"
                value={passwordConfirmation}
              />
            </div>

            <Button className="w-full" disabled={isSubmitting} type="submit">
              {isSubmitting ? 'Creando cuenta…' : 'Crear cuenta'}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-600">
            ¿Ya tenés cuenta?{' '}
            <Link className="font-semibold text-indigo-600 hover:text-indigo-700" to="/login">
              Iniciá sesión
            </Link>
          </p>
        </CardBody>
      </Card>
    </section>
  );
}
