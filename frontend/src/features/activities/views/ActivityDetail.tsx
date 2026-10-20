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
import { canManageActivity, isParticipant, isActivityCanceled } from '../activityPermissions';

export const ActivityDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const actividadId = id ?? '';
  const { user, token } = useAuth();

  const [activity, setActivity] = useState<Actividad | null>(null);
  const [pronostico, setPronostico] = useState<PronosticoRespuesta | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Estados para el modal de cancelación, baja y vista de listas
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [showLeaveModal, setShowLeaveModal] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [mostrarTodosParticipantes, setMostrarTodosParticipantes] = useState(false);
  const [mostrarHistorialReprogramaciones, setMostrarHistorialReprogramaciones] = useState(false);

  useDocumentTitle(activity ? activity.titulo : 'Detalle de Actividad');

  // Evaluación de permisos y estado
  const esParticipante = isParticipant(activity, user);
  const esOrganizador = canManageActivity(activity, user);
  const estaCancelada = isActivityCanceled(activity);

  const obtenerDetalle = useCallback(async () => {
    if (!actividadId) return;
    if (!token || !user) return;

    const actividadEncontrada = await activitiesApi.obtener(actividadId, token);
    let pronosticoEncontrado: PronosticoRespuesta | null = null;

    const yaParticipa = actividadEncontrada?.participantes.some((p) => p.id === user.id);
    const esOrg = actividadEncontrada?.organizador?.id === user.id;
    if (actividadEncontrada && (yaParticipa || esOrg) && actividadEncontrada.estadoActividad !== 'CANCELADA') {
      try {
        pronosticoEncontrado = await activitiesApi.clima(actividadId, token);
      } catch (error) {
        console.error('Error al obtener el pronóstico del clima:', error);
      }
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

  async function handleJoin() {
    if (!token || !user || !activity || estaCancelada) return;
    setBusy(true);
    setError('');
    try {
      await activitiesApi.unirse(activity.id, token);
      await cargarTodo();
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos sumarte a la actividad.');
    } finally {
      setBusy(false);
    }
  }

  async function handleConfirmLeave() {
    if (!token || !user || !activity || estaCancelada) return;
    setLeaving(true);
    setError('');
    try {
      await activitiesApi.bajarse(activity.id, token);
      setShowLeaveModal(false);
      await cargarTodo();
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos darte de baja de la actividad.');
    } finally {
      setLeaving(false);
    }
  }

  function handleParticipationClick() {
    if (esParticipante) {
      setShowLeaveModal(true);
    } else {
      void handleJoin();
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

  // 🔹 Formateador de Horarios y Duración
  const getFormattedSchedule = () => {
    if (!activity) return { fechaTexto: '', horarioTexto: '', duracionTexto: '' };
    try {
      const inicio = new Date(activity.fecha);
      const duracion = activity.duracionEstimada || 1;
      const fin = new Date(inicio.getTime() + duracion * 3600 * 1000);

      const fechaRaw = inicio.toLocaleDateString('es-AR', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      });
      const fechaTexto = fechaRaw.charAt(0).toUpperCase() + fechaRaw.slice(1);

      const horaInicio = inicio.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' });
      const horaFin = fin.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' });

      return {
        fechaTexto,
        horarioTexto: `${horaInicio} a ${horaFin} hs`,
        duracionTexto: `${duracion} ${duracion === 1 ? 'hora' : 'horas'}`
      };
    } catch {
      return {
        fechaTexto: activity.fecha,
        horarioTexto: '',
        duracionTexto: `${activity.duracionEstimada || 1} hs`
      };
    }
  };

  // 🔹 Semáforo Climático Inteligente
  const checkWeatherStatus = () => {
    if (!pronostico || !activity?.reglasClima) return null;
    const { probabilidadLluvia, viento, temperatura } = pronostico.pronosticoFuturo;
    const { maxProbabilidadLluvia, maxViento, minTemperatura, maxTemperatura } = activity.reglasClima;

    const alertas: string[] = [];

    const lluviaOk = maxProbabilidadLluvia == null || probabilidadLluvia <= maxProbabilidadLluvia;
    if (!lluviaOk) {
      alertas.push(`Lluvia (${probabilidadLluvia}%) supera el máx. pactado de ${maxProbabilidadLluvia}%`);
    }

    const vientoOk = maxViento == null || viento <= maxViento;
    if (!vientoOk) {
      alertas.push(`Viento (${viento} km/h) supera el límite de ${maxViento} km/h`);
    }

    const tempMinOk = minTemperatura == null || temperatura >= minTemperatura;
    if (!tempMinOk) {
      alertas.push(`Temperatura (${temperatura}°C) es menor a la mínima de ${minTemperatura}°C`);
    }

    const tempMaxOk = maxTemperatura == null || temperatura <= maxTemperatura;
    if (!tempMaxOk) {
      alertas.push(`Temperatura (${temperatura}°C) supera la máxima de ${maxTemperatura}°C`);
    }

    const apto = alertas.length === 0;

    return {
      apto,
      alertas,
      lluviaOk,
      vientoOk,
      tempOk: tempMinOk && tempMaxOk
    };
  };

  // 🔹 Badge de estado de ciclo de vida
  const getEstadoBadge = (estado: string) => {
    switch (estado) {
      case 'CANCELADA':
        return <Badge variant="error" className="px-3 py-1 font-bold">CANCELADA</Badge>;
      case 'CONFIRMADA':
        return <Badge variant="success" className="px-3 py-1 font-bold">CONFIRMADA</Badge>;
      case 'REPROGRAMADA':
        return <Badge variant="warning" className="px-3 py-1 font-bold bg-purple-100 text-purple-800 border-purple-200">REPROGRAMADA</Badge>;
      case 'FINALIZADA':
        return <Badge variant="neutral" className="px-3 py-1 font-bold bg-gray-100 text-gray-700">FINALIZADA</Badge>;
      case 'PROPUESTA':
      default:
        return <Badge variant="warning" className="px-3 py-1 font-bold">PROPUESTA</Badge>;
    }
  };

  // 🔹 Estado declarativo para el botón de Acción de Participación
  const getParticipationButtonConfig = () => {
    if (estaCancelada) {
      return { text: 'Actividad Cancelada', disabled: true, variant: 'secondary' as const };
    }
    if (activity?.estadoActividad === 'FINALIZADA') {
      return { text: 'Actividad Finalizada', disabled: true, variant: 'secondary' as const };
    }
    if (esParticipante) {
      return { text: 'Bajarme de la actividad', disabled: busy || leaving, variant: 'danger' as const };
    }
    const isFull = activity ? activity.participantes.length >= activity.maximoParticipantes : false;
    if (isFull) {
      return { text: 'Actividad Completa (Sin cupos)', disabled: true, variant: 'secondary' as const };
    }
    return { text: 'Sumarme a la actividad', disabled: busy || leaving, variant: 'primary' as const };
  };

  if (!actividadId) {
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
        <h2 className="text-2xl font-bold text-gray-900">
          {error || 'Actividad no encontrada'}
        </h2>
        <Link to="/activities" className="text-indigo-600 hover:underline mt-4 inline-block">Volver al buscador</Link>
      </div>
    );
  }

  const isFull = activity.participantes.length >= activity.maximoParticipantes;
  const participationBtn = getParticipationButtonConfig();
  const schedule = getFormattedSchedule();
  const weatherStatus = checkWeatherStatus();

  return (
    <div className="max-w-4xl mx-auto space-y-6">

      {/* Navegación de retorno */}
      <div>
        <Link
          to="/activities"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-indigo-600 hover:text-indigo-800 transition-colors"
        >
          <span>←</span> Volver al listado de actividades
        </Link>
      </div>

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

      {/* Cartel Informativo si está Finalizada */}
      {activity.estadoActividad === 'FINALIZADA' && (
        <div className="bg-gray-100 border border-gray-200 text-gray-800 p-4 rounded-xl flex items-center gap-3">
          <span className="text-2xl">🏁</span>
          <div>
            <h4 className="font-bold">Actividad Finalizada</h4>
            <p className="text-sm text-gray-600">Esta actividad ya se ha realizado.</p>
          </div>
        </div>
      )}

      {/* Banner Informativo si está Reprogramada */}
      {activity.estadoActividad === 'REPROGRAMADA' && (
        <div className="bg-purple-50 border border-purple-200 text-purple-900 p-4 rounded-xl flex items-start gap-3">
          <span className="text-2xl">🔄</span>
          <div className="space-y-1 w-full">
            <h4 className="font-bold text-purple-950">Actividad Reprogramada</h4>
            <p className="text-sm text-purple-800">
              Esta actividad fue reprogramada debido a condiciones climáticas u organizativas. Nueva fecha pactada: <strong>{schedule.fechaTexto} ({schedule.horarioTexto})</strong>.
            </p>
            {activity.cambiosFecha && activity.cambiosFecha.length > 0 && (
              <div className="pt-2">
                <button
                  type="button"
                  onClick={() => setMostrarHistorialReprogramaciones(prev => !prev)}
                  className="text-xs font-semibold text-purple-700 hover:text-purple-950 underline cursor-pointer"
                >
                  {mostrarHistorialReprogramaciones
                    ? 'Ocultar historial de reprogramaciones'
                    : `Ver historial de reprogramaciones (${activity.cambiosFecha.length})`}
                </button>
                {mostrarHistorialReprogramaciones && (
                  <div className="mt-2 space-y-1.5 bg-white/80 border border-purple-200 rounded-lg p-3 text-xs">
                    {activity.cambiosFecha.map((cambio, idx) => (
                      <div
                        key={idx}
                        className="flex flex-col sm:flex-row sm:justify-between text-purple-900 py-1 border-b border-purple-100 last:border-b-0 gap-1"
                      >
                        <span className="text-purple-700 font-medium">
                          Reprogramado el {new Date(cambio.fecha).toLocaleDateString('es-AR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })} hs:
                        </span>
                        <span>
                          De <span className="line-through text-purple-400">{new Date(cambio.fechaAntigua).toLocaleString('es-AR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })} hs</span> a{' '}
                          <span className="font-bold text-purple-950">{new Date(cambio.fechaNueva).toLocaleString('es-AR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })} hs</span>
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Header Info */}
      <div className="bg-white p-6 sm:p-8 rounded-2xl shadow-sm border border-gray-100 relative overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div className="flex items-center gap-2 flex-wrap">
            {getEstadoBadge(activity.estadoActividad)}
            <Badge variant="info" className="px-3 py-1 font-semibold">{activity.tipoActividad}</Badge>
          </div>

          {esOrganizador && !estaCancelada && (
            <Link to={`/activities/${activity.id}/weather-config`}>
              <Button variant="secondary" className="text-xs py-1.5 px-3">
                ⚙️ Configurar clima
              </Button>
            </Link>
          )}
        </div>
        
        <h1 className="text-3xl font-bold mb-2 text-gray-900">
          {activity.titulo}
        </h1>

        {/* Organizador */}
        <div className="flex items-center gap-2 text-sm text-gray-500 mb-4">
          <span>Organizado por</span>
          <span className="font-semibold text-gray-800 flex items-center gap-1.5">
            <span className="w-5 h-5 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-[11px] font-bold">
              {(activity.organizador?.username || 'U').charAt(0).toUpperCase()}
            </span>
            {activity.organizador?.username}
            {user?.id === activity.organizador?.id && (
              <span className="text-[10px] bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded font-normal">Tú</span>
            )}
          </span>
        </div>

        <p className="text-gray-600 text-base mb-6 leading-relaxed">{activity.descripcion}</p>

        {/* Cuadrícula de Fecha, Horario y Ubicación */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-gray-700 pt-5 border-t border-gray-100">
          <div className="flex items-start gap-3">
            <span className="text-2xl">📅</span>
            <div>
              <p className="text-xs text-gray-500 uppercase font-semibold tracking-wider">Fecha y Horario</p>
              <p className="font-semibold text-gray-900">{schedule.fechaTexto}</p>
              <p className="text-xs text-gray-600 mt-0.5">
                {schedule.horarioTexto} <span className="text-gray-400">({schedule.duracionTexto})</span>
              </p>
            </div>
          </div>

          <div className="flex items-start gap-3">
            <span className="text-2xl">📍</span>
            <div>
              <p className="text-xs text-gray-500 uppercase font-semibold tracking-wider">Ubicación</p>
              <p className="font-semibold text-gray-900">{activity.ubicacion.ciudad}</p>
              {activity.ubicacion.latitud != null && activity.ubicacion.longitud != null && (
                <a
                  href={`https://www.google.com/maps/search/?api=1&query=${activity.ubicacion.latitud},${activity.ubicacion.longitud}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-indigo-600 hover:text-indigo-800 font-medium inline-flex items-center gap-0.5 mt-0.5 hover:underline"
                >
                  Ver en Google Maps ↗
                </a>
              )}
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
              ) : (!esParticipante && !esOrganizador) ? (
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-6 text-center">
                  <p className="text-gray-600 mb-2">Sumate a la actividad para ver el pronóstico y participar de votaciones si el clima empeora.</p>
                </div>
              ) : pronostico ? (
                <div className="space-y-4">
                  {/* Semáforo Inteligente General */}
                  {weatherStatus && (
                    weatherStatus.apto ? (
                      <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-900 flex items-center gap-2">
                        <span className="text-base">☀️</span>
                        <div>
                          <span className="font-bold">Condiciones Óptimas:</span> El pronóstico actual cumple con todas las reglas climáticas acordadas.
                        </div>
                      </div>
                    ) : (
                      <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-950 space-y-1.5">
                        <div className="flex items-center gap-2 font-bold text-rose-900">
                          <span className="text-base">⚠️</span>
                          <span>Condiciones Desfavorables Detectadas:</span>
                        </div>
                        <ul className="list-disc list-inside space-y-0.5 text-[11px] text-rose-800 pl-1">
                          {weatherStatus.alertas.map((alerta, idx) => (
                            <li key={idx}>{alerta}</li>
                          ))}
                        </ul>
                        <p className="text-[11px] text-rose-700 italic pt-0.5">
                          Si estas condiciones persisten con {activity.horasAnticipacion ?? 24} hs de anticipación, se habilitará la votación para reprogramar.
                        </p>
                      </div>
                    )
                  )}

                  {/* Grid de 3 Métricas con Semáforo Individual */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    {/* Temperatura */}
                    <div className={`p-3.5 rounded-xl border ${
                      weatherStatus
                        ? weatherStatus.tempOk
                          ? 'bg-emerald-50/60 border-emerald-200 text-emerald-950'
                          : 'bg-rose-50 border-rose-200 text-rose-950'
                        : 'bg-gray-50 border-gray-200 text-gray-900'
                    }`}>
                      <div className="flex justify-between items-center">
                        <span className="text-xs uppercase font-semibold tracking-wider text-gray-500">Temperatura</span>
                        <span className="text-base">🌡️</span>
                      </div>
                      <p className="text-2xl font-bold mt-1">
                        {pronostico.pronosticoFuturo.temperatura}°C
                      </p>
                      {weatherStatus && (
                        <p className={`text-[11px] font-medium mt-1 ${weatherStatus.tempOk ? 'text-emerald-700' : 'text-rose-700 font-bold'}`}>
                          {weatherStatus.tempOk ? '✓ Dentro de rango' : '✕ Fuera de rango'}
                        </p>
                      )}
                    </div>

                    {/* Viento */}
                    <div className={`p-3.5 rounded-xl border ${
                      weatherStatus
                        ? weatherStatus.vientoOk
                          ? 'bg-emerald-50/60 border-emerald-200 text-emerald-950'
                          : 'bg-rose-50 border-rose-200 text-rose-950'
                        : 'bg-gray-50 border-gray-200 text-gray-900'
                    }`}>
                      <div className="flex justify-between items-center">
                        <span className="text-xs uppercase font-semibold tracking-wider text-gray-500">Viento</span>
                        <span className="text-base">💨</span>
                      </div>
                      <p className="text-2xl font-bold mt-1">
                        {pronostico.pronosticoFuturo.viento} <span className="text-sm font-normal">km/h</span>
                      </p>
                      {weatherStatus && (
                        <p className={`text-[11px] font-medium mt-1 ${weatherStatus.vientoOk ? 'text-emerald-700' : 'text-rose-700 font-bold'}`}>
                          {weatherStatus.vientoOk ? '✓ Aceptable' : '✕ Supera límite'}
                        </p>
                      )}
                    </div>

                    {/* Lluvia */}
                    <div className={`p-3.5 rounded-xl border ${
                      weatherStatus
                        ? weatherStatus.lluviaOk
                          ? 'bg-emerald-50/60 border-emerald-200 text-emerald-950'
                          : 'bg-rose-50 border-rose-200 text-rose-950'
                        : 'bg-gray-50 border-gray-200 text-gray-900'
                    }`}>
                      <div className="flex justify-between items-center">
                        <span className="text-xs uppercase font-semibold tracking-wider text-gray-500">Lluvia</span>
                        <span className="text-base">🌧️</span>
                      </div>
                      <p className="text-2xl font-bold mt-1">
                        {pronostico.pronosticoFuturo.probabilidadLluvia}%
                      </p>
                      {weatherStatus && (
                        <p className={`text-[11px] font-medium mt-1 ${weatherStatus.lluviaOk ? 'text-emerald-700' : 'text-rose-700 font-bold'}`}>
                          {weatherStatus.lluviaOk ? '✓ Aceptable' : '✕ Supera límite'}
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-gray-500">No pudimos obtener el pronóstico.</p>
              )}

              {/* Parámetros climáticos y reprogramación configurados */}
              <div className="mt-5 pt-4 border-t border-gray-100">
                <div className="flex justify-between items-center mb-3">
                  <h3 className="text-xs uppercase font-bold tracking-wider text-gray-500 flex items-center gap-1.5">
                    <span>⚙️</span> Condiciones Aceptables Configuradas
                  </h3>
                  {esOrganizador && !estaCancelada && (
                    <Link
                      to={`/activities/${activity.id}/weather-config`}
                      className="text-xs text-indigo-600 hover:text-indigo-800 font-semibold hover:underline"
                    >
                      {activity.reglasClima ? 'Modificar' : 'Configurar'}
                    </Link>
                  )}
                </div>

                {activity.reglasClima ? (
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                    <div className="bg-gray-50 border border-gray-200/80 rounded-xl p-3">
                      <p className="text-[10px] uppercase tracking-wider font-semibold text-gray-500">Lluvia Máx.</p>
                      <p className="text-base font-bold text-gray-900 mt-0.5">
                        {activity.reglasClima.maxProbabilidadLluvia}%
                      </p>
                    </div>

                    <div className="bg-gray-50 border border-gray-200/80 rounded-xl p-3">
                      <p className="text-[10px] uppercase tracking-wider font-semibold text-gray-500">Viento Máx.</p>
                      <p className="text-base font-bold text-gray-900 mt-0.5">
                        {activity.reglasClima.maxViento != null
                          ? `${activity.reglasClima.maxViento} km/h`
                          : <span className="text-gray-400 font-medium text-xs">Sin límite</span>}
                      </p>
                    </div>

                    <div className="bg-gray-50 border border-gray-200/80 rounded-xl p-3">
                      <p className="text-[10px] uppercase tracking-wider font-semibold text-gray-500">Temp. Aceptable</p>
                      <p className="text-xs font-bold text-gray-900 mt-1">
                        {activity.reglasClima.minTemperatura != null || activity.reglasClima.maxTemperatura != null
                          ? `${activity.reglasClima.minTemperatura != null ? `${activity.reglasClima.minTemperatura}°C` : '-∞'} a ${activity.reglasClima.maxTemperatura != null ? `${activity.reglasClima.maxTemperatura}°C` : '+∞'}`
                          : <span className="text-gray-400 font-medium text-xs">Sin límite</span>}
                      </p>
                    </div>

                    <div className="bg-gray-50 border border-gray-200/80 rounded-xl p-3">
                      <p className="text-[10px] uppercase tracking-wider font-semibold text-gray-500">Reprogramación</p>
                      <p className="text-xs font-bold text-gray-900 mt-1 truncate" title={activity.rangoReprogramacion ? `Hasta ${activity.rangoReprogramacion.dias}d (${activity.rangoReprogramacion.horaInicio} a ${activity.rangoReprogramacion.horaFinal} hs)` : `${activity.horasAnticipacion ?? 24}h aviso`}>
                        {activity.rangoReprogramacion
                          ? `Hasta ${activity.rangoReprogramacion.dias}d (${activity.rangoReprogramacion.horaInicio}-${activity.rangoReprogramacion.horaFinal}h)`
                          : `${activity.horasAnticipacion ?? 24}h aviso`}
                      </p>
                    </div>
                  </div>
                ) : (
                  <div className="bg-gray-50 border border-dashed border-gray-200 rounded-xl p-3 text-center">
                    <p className="text-xs text-gray-500">
                      Usa monitoreo estándar del sistema.
                      {esOrganizador && !estaCancelada && (
                        <Link to={`/activities/${activity.id}/weather-config`} className="text-indigo-600 font-medium hover:underline ml-1">
                          Personalizar reglas
                        </Link>
                      )}
                    </p>
                  </div>
                )}
              </div>
            </CardBody>
          </Card>

          {/* Sección de votaciones desactivada si la actividad está cancelada */}
          {!estaCancelada && (esParticipante || esOrganizador) && (
            <VotingSection
              actividadId={activity.id}
              organizadorId={activity.organizador.id}
              onActivityChanged={cargarTodo}
            />
          )}
        </div>

        {/* Columna Derecha: Panel de Participación y Lista de Inscriptos */}
        <div className="space-y-6">
          <Card className="sticky top-6">
            <CardBody>
              {/* Tarjeta distintiva para el Organizador */}
              {esOrganizador && (
                <div className="p-3 bg-indigo-50 border border-indigo-200 rounded-xl text-xs text-indigo-900 flex items-start gap-2.5 font-medium mb-4">
                  <span className="text-lg shrink-0">👑</span>
                  <div>
                    <span className="font-bold block text-sm">Eres el organizador</span>
                    <p className="text-xs text-indigo-700 mt-0.5">
                      Gestionas los cupos, las reglas de clima y la coordinación de esta actividad.
                    </p>
                  </div>
                </div>
              )}

              <h3 className="text-lg font-bold text-gray-900 mb-2">Cupos y Quórum</h3>
              
              {/* Barra de progreso de cupos */}
              <div className="w-full bg-gray-200 rounded-full h-2.5 mb-2">
                <div
                  className={`h-2.5 rounded-full ${estaCancelada ? 'bg-gray-400' : isFull ? 'bg-red-500' : 'bg-indigo-600'}`}
                  style={{
                    width: `${activity.maximoParticipantes > 0
                      ? Math.min(100, (activity.participantes.length / activity.maximoParticipantes) * 100)
                      : 0}%`
                  }}
                ></div>
              </div>
              <div className="flex justify-between text-xs text-gray-600 mb-3 font-medium">
                <span>{activity.participantes.length} inscriptos</span>
                <span>Máx. {activity.maximoParticipantes}</span>
              </div>

              {/* Indicador de Quórum */}
              {!estaCancelada && activity.estadoActividad !== 'FINALIZADA' && (
                <div className="mb-5">
                  {activity.participantes.length >= activity.minimoParticipantes ? (
                    <div className="p-2.5 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 flex items-center gap-2 font-medium">
                      <span className="text-base">🎉</span>
                      <div>
                        <span className="font-bold">¡Quórum asegurado!</span>
                        <p className="text-[11px] text-emerald-700">Se alcanzó el mínimo de {activity.minimoParticipantes} inscriptos.</p>
                      </div>
                    </div>
                  ) : (
                    <div className="p-2.5 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 flex items-center gap-2 font-medium">
                      <span className="text-base">⏳</span>
                      <div>
                        <span className="font-bold">Faltan {activity.minimoParticipantes - activity.participantes.length} inscriptos</span>
                        <p className="text-[11px] text-amber-700">Mínimo para confirmar: {activity.minimoParticipantes} personas.</p>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Botón principal accionado dinámicamente según el estado */}
              {!esOrganizador && (
                <Button
                  variant={participationBtn.variant}
                  onClick={handleParticipationClick}
                  disabled={participationBtn.disabled}
                  className="w-full text-base py-3 cursor-pointer"
                >
                  {busy ? 'Procesando...' : participationBtn.text}
                </Button>
              )}

              {/* Sección Social: Lista de Participantes Inscriptos */}
              <div className="mt-6 pt-4 border-t border-gray-100">
                <div className="flex justify-between items-center mb-3">
                  <h4 className="text-xs uppercase font-bold tracking-wider text-gray-500">
                    Inscriptos ({activity.participantes.length})
                  </h4>
                  {activity.participantes.length > 4 && (
                    <button
                      type="button"
                      onClick={() => setMostrarTodosParticipantes(prev => !prev)}
                      className="text-xs text-indigo-600 hover:text-indigo-800 font-medium cursor-pointer"
                    >
                      {mostrarTodosParticipantes ? 'Ver menos' : `Ver todos (${activity.participantes.length})`}
                    </button>
                  )}
                </div>

                {activity.participantes.length === 0 ? (
                  <p className="text-xs text-gray-400 italic">Aún no hay inscriptos. ¡Sé el primero!</p>
                ) : (
                  <div className="space-y-2">
                    {(mostrarTodosParticipantes
                      ? activity.participantes
                      : activity.participantes.slice(0, 4)
                    ).map((participante) => {
                      const isCreator = participante.id === activity.organizador?.id;
                      const isMe = user?.id === participante.id;
                      const initial = (participante.username || 'U').charAt(0).toUpperCase();

                      return (
                        <div
                          key={participante.id}
                          className="flex items-center justify-between p-2 rounded-lg bg-gray-50 border border-gray-100 text-xs"
                        >
                          <div className="flex items-center gap-2.5 truncate">
                            <div className="w-7 h-7 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs shrink-0">
                              {initial}
                            </div>
                            <span className="font-medium text-gray-900 truncate">
                              {participante.username}
                            </span>
                          </div>

                          <div className="flex items-center gap-1 shrink-0 ml-2">
                            {isCreator && (
                              <span className="bg-amber-100 text-amber-800 text-[10px] font-semibold px-2 py-0.5 rounded-full flex items-center gap-0.5">
                                👑 Org.
                              </span>
                            )}
                            {isMe && (
                              <span className="bg-indigo-100 text-indigo-800 text-[10px] font-semibold px-2 py-0.5 rounded-full">
                                Tú
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Botón de Cancelar Actividad (solo visible si es organizador y NO está cancelada aún) */}
              {esOrganizador && !estaCancelada && (
                <div className="mt-6 pt-4 border-t border-gray-100">
                  <Button
                    variant="danger"
                    type="button"
                    onClick={() => setShowCancelModal(true)}
                    className="w-full text-sm cursor-pointer"
                  >
                    🚫 Cancelar actividad
                  </Button>
                </div>
              )}
            </CardBody>
          </Card>
        </div>

      </div>

      {/* Modal de Confirmación de Baja de Participante */}
      {showLeaveModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150"
          role="dialog"
          aria-modal="true"
          aria-labelledby="leave-modal-title"
        >
          <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl border border-gray-100 animate-in zoom-in-95 duration-150">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-amber-100 text-amber-700 flex items-center justify-center text-xl shrink-0 font-bold">
                ⚠️
              </div>
              <div>
                <h3 id="leave-modal-title" className="text-lg font-bold text-gray-900">
                  ¿Deseas bajarte de la actividad?
                </h3>
                <p className="text-xs text-gray-500">Confirmación de desinscripción</p>
              </div>
            </div>

            <div className="text-sm text-gray-600 space-y-2">
              <p>
                Vas a darte de baja de <strong className="text-gray-900 font-semibold">"{activity.titulo}"</strong>.
              </p>
              {activity.participantes.length >= activity.maximoParticipantes ? (
                <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 text-xs text-amber-900">
                  <span className="font-bold">⚠️ Atención:</span> La actividad está actualmente completa ({activity.participantes.length}/{activity.maximoParticipantes}). Si te bajas ahora, liberarás tu lugar y otro usuario podría ocuparlo.
                </div>
              ) : (
                <div className="bg-gray-50 border border-gray-200 rounded-xl p-3 text-xs text-gray-600">
                  Liberarás tu cupo en la actividad ({activity.participantes.length}/{activity.maximoParticipantes}). Si decides volver a unirte más tarde, dependerá de la disponibilidad de lugares.
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 pt-3 border-t border-gray-100">
              <Button
                variant="secondary"
                type="button"
                disabled={leaving}
                onClick={() => setShowLeaveModal(false)}
              >
                Volver
              </Button>
              <Button
                variant="danger"
                type="button"
                disabled={leaving}
                onClick={handleConfirmLeave}
              >
                {leaving ? 'Bajándome...' : 'Sí, darme de baja'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Modal de Confirmación de Cancelación */}
      {showCancelModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150"
          role="dialog"
          aria-modal="true"
          aria-labelledby="cancel-modal-title"
        >
          <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl border border-gray-100 animate-in zoom-in-95 duration-150">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-red-100 text-red-700 flex items-center justify-center text-xl shrink-0 font-bold">
                🚫
              </div>
              <div>
                <h3 id="cancel-modal-title" className="text-lg font-bold text-gray-900">
                  ¿Cancelar esta actividad?
                </h3>
                <p className="text-xs text-gray-500">Acción irreversible</p>
              </div>
            </div>

            <div className="text-sm text-gray-600 space-y-2">
              <p>
                Esta acción no se puede deshacer. Se cancelará la actividad <strong className="text-gray-900 font-semibold">"{activity.titulo}"</strong> de forma definitiva.
              </p>
              <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-900">
                Se notificará a todos los inscriptos ({activity.participantes.length} {activity.participantes.length === 1 ? 'persona' : 'personas'}) y se cerrarán las votaciones asociadas.
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-3 border-t border-gray-100">
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
