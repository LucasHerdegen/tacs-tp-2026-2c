import React, { useCallback, useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
// import { useNavigate } from 'react-router-dom'; // Hace que falle build mientras no se use.
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { useAuth } from '../../auth/authContext';
import { ApiError } from '../../../lib/api';
import { activitiesApi } from '../activitiesApi';
import { VotingSection } from '../../voting/components/VotingSection';
import type { Actividad, PronosticoRespuesta } from '../types';
import { canManageActivity, isParticipant, isActivityCanceled } from '../activityPermissions';

export const ActivityDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const actividadId = Number(id);
  const { user, token } = useAuth();
  // const navigate = useNavigate(); // Hace que falle build mientras no se use.

  const [activity, setActivity] = useState<Actividad | null>(null);
  const [pronostico, setPronostico] = useState<PronosticoRespuesta | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Estados para el modal de cancelación
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [canceling, setCanceling] = useState(false);

  useDocumentTitle(activity ? activity.titulo : 'Detalle de Actividad');

  // Evaluación de permisos y estado
  const esParticipante = isParticipant(activity, user);
  const esOrganizador = canManageActivity(activity, user);
  const estaCancelada = isActivityCanceled(activity);

  const obtenerDetalle = useCallback(async () => {
    if (Number.isNaN(actividadId)) return;
    if (!token || !user) return;

    const actividadEncontrada = await activitiesApi.obtener(actividadId, token);
    let pronosticoEncontrado: PronosticoRespuesta | null = null;

    const yaParticipa = actividadEncontrada?.participantes.some((p) => p.id === user.id);
    if (actividadEncontrada && yaParticipa && actividadEncontrada.estadoActividad !== 'CANCELADA') {
      pronosticoEncontrado = await activitiesApi.clima(actividadId, user.id, token);
    }

    return { actividadEncontrada, pronosticoEncontrado };
  }, [actividadId, token, user]);

  const cargarTodo = useCallback(async () => {
    try {
      const detalle = await obtenerDetalle();
      if (!detalle) return;
      setActivity(detalle.actividadEncontrada);
      setPronostico(detalle.pronosticoEncontrado);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cargar la actividad.');
    } finally {
      setLoading(false);
    }
  }, [obtenerDetalle]);

  useEffect(() => {
    let active = true;

    obtenerDetalle()
      .then((detalle) => {
        if (!active || !detalle) return;
        setActivity(detalle.actividadEncontrada);
        setPronostico(detalle.pronosticoEncontrado);
      })
      .catch((requestError) => {
        if (!active) return;
        setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cargar la actividad.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [obtenerDetalle]);

  async function handleJoinLeave() {
    if (!token || !user || !activity || estaCancelada) return;
    setBusy(true);
    setError('');
    try {
      if (esParticipante) {
        await activitiesApi.bajarse(activity.id, user.id, token);
      } else {
        await activitiesApi.unirse(activity.id, user.id, token);
      }
      await cargarTodo();
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos actualizar tu participación.');
    } finally {
      setBusy(false);
    }
  }

  async function handleCancelActivity() {
    if (!token || !activity || estaCancelada) return;
    setCanceling(true);
    setError('');
    try {
      await activitiesApi.cancelar(activity.id, token);
      setShowCancelModal(false);
      await cargarTodo(); // Recargamos para actualizar el estado a CANCELADA
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cancelar la actividad.');
      setShowCancelModal(false);
    } finally {
      setCanceling(false);
    }
  }

  // 🔹 Estado declarativo para el botón de Acción de Participación
  const getParticipationButtonConfig = () => {
    if (estaCancelada) {
      return { text: 'Actividad Cancelada', disabled: true, variant: 'secondary' as const };
    }
    if (esParticipante) {
      return { text: 'Bajarme de la actividad', disabled: busy, variant: 'danger' as const };
    }
    const isFull = activity ? activity.participantes.length >= activity.maximoParticipantes : false;
    if (isFull) {
      return { text: 'Actividad Llena', disabled: true, variant: 'primary' as const };
    }
    return { text: 'Sumarme a la actividad', disabled: busy, variant: 'primary' as const };
  };

  if (Number.isNaN(actividadId)) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold text-gray-900">Actividad no encontrada</h2>
        <Link to="/activities" className="text-indigo-600 hover:underline mt-4 inline-block">Volver al buscador</Link>
      </div>
    );
  }

  if (loading) {
    return <p className="text-center py-20 text-gray-500">Cargando actividad…</p>;
  }

  if (!activity) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold text-gray-900">Actividad no encontrada</h2>
        <Link to="/activities" className="text-indigo-600 hover:underline mt-4 inline-block">Volver al buscador</Link>
      </div>
    );
  }

  const isFull = activity.participantes.length >= activity.maximoParticipantes;
  const participationBtn = getParticipationButtonConfig();

  return (
    <div className="max-w-4xl mx-auto space-y-6">

      {error && (
        <div className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {error}
        </div>
      )}

      {/* Cartel Informativo si está Cancelada */}
      {estaCancelada && (
        <div className="bg-red-50 border border-red-200 text-red-800 p-4 rounded-xl flex items-center gap-3">
          <span className="text-2xl">🚫</span>
          <div>
            <h4 className="font-bold">Actividad Cancelada</h4>
            <p className="text-sm">El organizador ha cancelado esta actividad. No se permiten nuevas inscripciones ni modificaciones.</p>
          </div>
        </div>
      )}

      {/* Header Info */}
      <div className="bg-white p-6 sm:p-8 rounded-2xl shadow-sm border border-gray-100 relative overflow-hidden">
        <div className="absolute top-0 right-0 p-4 flex items-center gap-2">
          {estaCancelada ? (
            <Badge variant="error" className="px-3 py-1">CANCELADA</Badge>
          ) : (
            <Badge variant="info" className="px-3 py-1">{activity.tipoActividad}</Badge>
          )}

          {esOrganizador && !estaCancelada && (
            <Link to={`/activities/${activity.id}/weather-config`}>
              <Button variant="secondary" className="text-xs py-1 px-3">
                ⚙️ Configurar clima
              </Button>
            </Link>
          )}
        </div>
        
        <h1 className={`text-3xl font-bold mb-2 text-gray-900`}>
          {activity.titulo}
        </h1>
        <p className="text-gray-600 text-lg mb-6">{activity.descripcion}</p>

        <div className="flex flex-col sm:flex-row gap-6 text-gray-700">
          <div className="flex items-center gap-2">
            <span className="text-xl">📍</span>
            <div>
              <p className="text-xs text-gray-500 uppercase font-semibold tracking-wider">Ubicación</p>
              <p className="font-medium">{activity.ubicacion.barrio}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xl">📅</span>
            <div>
              <p className="text-xs text-gray-500 uppercase font-semibold tracking-wider">Fecha y Hora</p>
              <p className="font-medium">{new Date(activity.fecha).toLocaleString('es-AR', { dateStyle: 'long', timeStyle: 'short' })} hs</p>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">

        {/* Columna Izquierda: Clima + Votación */}
        <div className="md:col-span-2 space-y-6">
          <Card>
            <CardBody>
              <div className="flex justify-between items-center mb-4">
                <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
                  ⛅ Estado del Clima
                </h2>
                {esOrganizador && !estaCancelada && (
                  <Link to={`/activities/${activity.id}/weather-config`} className="text-xs text-indigo-600 hover:underline font-medium">
                    Ajustar parámetros
                  </Link>
                )}
              </div>

              {estaCancelada ? (
                <p className="text-sm text-gray-500 italic">El pronóstico no está disponible para actividades canceladas.</p>
              ) : !esParticipante ? (
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-6 text-center">
                  <p className="text-gray-600 mb-2">Sumate a la actividad para ver el pronóstico y participar de votaciones si el clima empeora.</p>
                </div>
              ) : pronostico ? (
                <div className="rounded-lg p-6 border bg-gray-50 border-gray-200">
                  <div className="flex gap-6 text-sm font-medium">
                    <span>Temp: {pronostico.pronosticoFuturo.temperatura}°C</span>
                    <span>Viento: {pronostico.pronosticoFuturo.viento} km/h</span>
                    <span>Lluvia: {pronostico.pronosticoFuturo.probabilidadLluvia}%</span>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-gray-500">No pudimos obtener el pronóstico.</p>
              )}
            </CardBody>
          </Card>

          {/* Sección de votaciones desactivada si la actividad está cancelada */}
          {!estaCancelada && (esParticipante || esOrganizador) && (
            <VotingSection actividadId={activity.id} organizadorId={activity.organizador.id} />
          )}
        </div>

        {/* Columna Derecha: Panel de Participación */}
        <div>
          <Card className="sticky top-6">
            <CardBody>
              <h3 className="text-lg font-bold text-gray-900 mb-2">Cupos</h3>
              <div className="w-full bg-gray-200 rounded-full h-2.5 mb-2">
                <div
                  className={`h-2.5 rounded-full ${estaCancelada ? 'bg-gray-400' : isFull ? 'bg-red-500' : 'bg-indigo-600'}`}
                  style={{ width: `${(activity.participantes.length / activity.maximoParticipantes) * 100}%` }}
                ></div>
              </div>
              <p className="text-sm text-gray-600 mb-6 font-medium">
                {activity.participantes.length} de {activity.maximoParticipantes} lugares ocupados
              </p>

              {/* Botón principal accionado dinámicamente según el estado */}
              {!esOrganizador && (<Button
                variant={participationBtn.variant}
                onClick={handleJoinLeave}
                disabled={participationBtn.disabled}
                className="w-full text-base py-3"
              >
                {participationBtn.text}
              </Button>)}

              <p className="text-xs text-gray-500 mt-4 text-center">
                Mínimo requerido para confirmar: {activity.minimoParticipantes}
              </p>

              {/* Botón de Cancelar Actividad (solo visible si es organizador y NO está cancelada aún) */}
              {esOrganizador && !estaCancelada && (
                <div className="mt-6 pt-4 border-t border-gray-100">
                  <Button
                    variant="danger"
                    type="button"
                    onClick={() => setShowCancelModal(true)}
                    className="w-full text-sm"
                  >
                    🚫 Cancelar actividad
                  </Button>
                </div>
              )}
            </CardBody>
          </Card>
        </div>

      </div>

      {/* Modal de Confirmación de Cancelación */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl">
            <h3 className="text-lg font-bold text-gray-900">
              ¿Cancelar esta actividad?
            </h3>
            <p className="text-sm text-gray-600">
              Esta acción no se puede deshacer. Se borrará la actividad <strong>"{activity.titulo}"</strong> y se notificara a los participantes.
            </p>

            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                type="button"
                disabled={canceling}
                onClick={() => setShowCancelModal(false)}
              >
                Volver
              </Button>
              <Button
                variant="danger"
                type="button"
                disabled={canceling}
                onClick={handleCancelActivity}
              >
                {canceling ? 'Cancelando...' : 'Sí, cancelar actividad'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
