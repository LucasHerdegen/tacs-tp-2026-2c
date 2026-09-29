import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/authContext';
import { formatFechaHora } from '../../../utils/dateTime';
import { notificationsApi } from '../notificationsApi';
import { TIPO_NOTIFICACION_ICON } from '../notificationLabels';
import type { NotificacionDto } from '../types';

const INTERVALO_POLLING_MS = 30_000;

export const NotificationBell: React.FC = () => {
  const { token } = useAuth();
  const navigate = useNavigate();

  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [notificaciones, setNotificaciones] = useState<NotificacionDto[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const contenedorRef = useRef<HTMLDivElement>(null);

  const actualizarContador = useCallback(async () => {
    if (!token) return;
    try {
      const { cantidad } = await notificationsApi.contarNoLeidas(token);
      setCount(cantidad);
    } catch {
      // Silencioso: un fallo puntual del polling no debe romper la UI.
    }
  }, [token]);

  // Polling del contador de no leídas
  useEffect(() => {
    if (!token) return;
    actualizarContador();
    const intervalo = setInterval(actualizarContador, INTERVALO_POLLING_MS);
    return () => clearInterval(intervalo);
  }, [token, actualizarContador]);

  // Cerrar al clickear afuera
  useEffect(() => {
    if (!open) return;
    function handleClickOutside(event: MouseEvent) {
      if (contenedorRef.current && !contenedorRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open]);

  async function handleToggle() {
    const abriendo = !open;
    setOpen(abriendo);
    if (!abriendo || !token) return;

    setLoadingList(true);
    try {
      const pagina = await notificationsApi.misNotificaciones(token, { size: 10 });
      setNotificaciones(pagina.content);

      // Al abrir, se marcan automáticamente como leídas.
      const habiaNoLeidas = pagina.content.some((n) => !n.leida) || count > 0;
      if (habiaNoLeidas) {
        await notificationsApi.marcarTodasComoLeidas(token);
        setNotificaciones((prev) => prev.map((n) => ({ ...n, leida: true })));
        setCount(0);
      }
    } catch {
      // Silencioso: dejamos el dropdown abierto igual, sin datos.
    } finally {
      setLoadingList(false);
    }
  }

  function handleClickNotificacion(notificacion: NotificacionDto) {
    setOpen(false);
    if (notificacion.actividadId) {
      navigate(`/activities/${notificacion.actividadId}`);
    }
  }

  return (
    <div className="relative" ref={contenedorRef}>
      <button
        type="button"
        onClick={handleToggle}
        className="relative p-2 rounded-md hover:bg-indigo-500 transition-colors"
        aria-label="Notificaciones"
      >
        <span className="text-xl">🔔</span>
        {count > 0 && (
          <span className="absolute -top-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-600 px-1 text-xs font-bold text-white">
            {count > 9 ? '9+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-80 max-h-96 overflow-y-auto bg-white rounded-xl shadow-lg border border-gray-100 z-50">
          <div className="px-4 py-3 border-b border-gray-100">
            <h3 className="text-sm font-bold text-gray-900">Notificaciones</h3>
          </div>

          {loadingList ? (
            <p className="text-sm text-gray-500 text-center py-6">Cargando…</p>
          ) : notificaciones.length === 0 ? (
            <p className="text-sm text-gray-500 text-center py-6">No tenés notificaciones.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {notificaciones.map((notificacion) => (
                <li key={notificacion.id}>
                  <button
                    type="button"
                    onClick={() => handleClickNotificacion(notificacion)}
                    className="w-full text-left px-4 py-3 hover:bg-gray-50 transition-colors flex gap-3"
                  >
                    <span className="text-lg shrink-0">{TIPO_NOTIFICACION_ICON[notificacion.tipo]}</span>
                    <div className="min-w-0">
                      <p className="text-sm text-gray-800">{notificacion.contenido}</p>
                      <p className="text-xs text-gray-400 mt-1">{formatFechaHora(notificacion.fechaCreacion)}</p>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
};