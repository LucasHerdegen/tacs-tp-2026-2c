import React, { useCallback, useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { useAuth } from '../../auth/authContext';
import { ApiError } from '../../../lib/api';
import { activitiesApi } from '../activitiesApi';
import { VotingSection } from '../../voting/components/VotingSection';
import type { Actividad, PronosticoRespuesta } from '../types';

export const ActivityDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const actividadId = Number(id);
  const { user, token } = useAuth();

  const [activity, setActivity] = useState<Actividad | null>(null);
  const [pronostico, setPronostico] = useState<PronosticoRespuesta | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useDocumentTitle(activity ? activity.titulo : 'Detalle de Actividad');

  const esParticipante = !!activity && !!user && activity.participantes.some((p) => p.id === user.id);

  const cargarTodo = useCallback(async () => {
    if (!token || !user || Number.isNaN(actividadId)) return;
    setLoading(true);
    setError('');
    try {
      const actividadEncontrada = await activitiesApi.obtener(actividadId, token);
      setActivity(actividadEncontrada);

      const yaParticipa = actividadEncontrada?.participantes.some((p) => p.id === user.id);
      if (actividadEncontrada && yaParticipa) {
        const clima = await activitiesApi.clima(actividadId, user.id, token);
        setPronostico(clima);
      }
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cargar la actividad.');
    } finally {
      setLoading(false);
    }
  }, [actividadId, token, user]);

  useEffect(() => {
    cargarTodo();
  }, [cargarTodo]);

  async function handleJoinLeave() {
    if (!token || !user || !activity) return;
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

  return (
    <div className="max-w-4xl mx-auto space-y-6">

      {error && (
        <div className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {error}
        </div>
      )}

      {/* Header Info */}
      <div className="bg-white p-6 sm:p-8 rounded-2xl shadow-sm border border-gray-100 relative overflow-hidden">
        <div className="absolute top-0 right-0 p-4">
          <Badge variant="info" className="px-3 py-1">{activity.tipoActividad}</Badge>
        </div>
        <h1 className="text-3xl font-bold text-gray-900 mb-2">{activity.titulo}</h1>
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

        {/* Columna Izquierda: Clima + Votación (US8-US11) */}
        <div className="md:col-span-2 space-y-6">
          <Card>
            <CardBody>
              <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
                ⛅ Estado del Clima
              </h2>

              {!esParticipante ? (
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

          {esParticipante && (
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
                  className={`h-2.5 rounded-full ${isFull ? 'bg-red-500' : 'bg-indigo-600'}`}
                  style={{ width: `${(activity.participantes.length / activity.maximoParticipantes) * 100}%` }}
                ></div>
              </div>
              <p className="text-sm text-gray-600 mb-6 font-medium">
                {activity.participantes.length} de {activity.maximoParticipantes} lugares ocupados
              </p>

              <Button
                variant={esParticipante ? 'danger' : 'primary'}
                onClick={handleJoinLeave}
                disabled={busy || (!esParticipante && isFull)}
                className="w-full text-base py-3"
              >
                {esParticipante ? 'Bajarme de la actividad' : isFull ? 'Actividad Llena' : 'Sumarme a la actividad'}
              </Button>

              <p className="text-xs text-gray-500 mt-4 text-center">
                Mínimo requerido para confirmar: {activity.minimoParticipantes}
              </p>
            </CardBody>
          </Card>
        </div>

      </div>
    </div>
  );
};