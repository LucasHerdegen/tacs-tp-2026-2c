import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../../components/ui/Button';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { useAuth } from '../../auth/authContext';
import { activitiesApi } from '../../activities/activitiesApi';
import type { Actividad, TipoActividad } from '../../activities/types';
import { ESTADO_BADGE } from '../../activities/activityLabels';
import { formatFechaHora } from '../../../utils/dateTime';

const TIPO_ACTIVIDAD_LABEL: Record<TipoActividad, string> = {
  AIRE_LIBRE: 'Aire libre',
  TECHADA: 'Techada',
  MIXTA: 'Mixta',
};

export const Home: React.FC = () => {
  useDocumentTitle('Inicio');
  const navigate = useNavigate();
  const { user, token } = useAuth();

  const [actividades, setActividades] = useState<Actividad[]>([]);
  const [loadedToken, setLoadedToken] = useState<string | null>(null);
  const loading = Boolean(token) && loadedToken !== token;

  useEffect(() => {
    if (!token) return;

    let ignore = false;

    activitiesApi
      .buscar(null, null, null, null, null, 0, 5, token)
      .then((res) => {
        if (!ignore) {
          setActividades(res.content);
        }
      })
      .catch(() => {
        if (!ignore) {
          setActividades([]);
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoadedToken(token);
        }
      });

    return () => {
      ignore = true;
    };
  }, [token]);

  return (
    <div className="space-y-10">
      {/* Hero Section */}
      <section className="text-center py-12 px-4 sm:px-6 lg:px-8 bg-white rounded-2xl shadow-sm border border-gray-100">
        <h1 className="text-4xl font-extrabold text-gray-900 sm:text-5xl">
          Que el clima no arruine tus planes
        </h1>
        <p className="mt-4 text-xl text-gray-500 max-w-2xl mx-auto">
          Organizá asados, partidos o salidas. Nosotros monitoreamos el pronóstico por vos y te avisamos si hay que reprogramar.
        </p>
        <div className="mt-8 flex justify-center gap-4">
          {user ? (
            <>
              <Button variant="primary" className="text-lg px-8 py-3" onClick={() => navigate('/activities/new')}>
                Crear Actividad
              </Button>
              <Button variant="secondary" className="text-lg px-8 py-3" onClick={() => navigate('/activities')}>
                Buscar Actividades
              </Button>
            </>
          ) : (
            <>
              <Button variant="primary" className="text-lg px-8 py-3" onClick={() => navigate('/register')}>
                Sumate a la comunidad
              </Button>
              <Button variant="secondary" className="text-lg px-8 py-3" onClick={() => navigate('/login')}>
                Iniciar Sesión
              </Button>
            </>
          )}
        </div>
      </section>

      {/* Actividades Reales o Cómo Funciona */}
      {user ? (
        <section>
          <div className="flex justify-between items-center mb-6">
            <h2 className="text-2xl font-bold text-gray-900">Actividades Recientes</h2>
            <Button variant="secondary" className="text-sm" onClick={() => navigate('/activities')}>
              Ver todas →
            </Button>
          </div>

          {loading ? (
            <div className="text-center py-12 text-gray-500">
              Cargando actividades…
            </div>
          ) : actividades.length > 0 ? (
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {actividades.map((actividad) => {
                const estadoBadge = ESTADO_BADGE[actividad.estadoActividad];
                return (
                  <Card key={actividad.id} className="flex flex-col">
                    <CardBody className="flex-grow">
                      <div className="flex justify-between items-start mb-4">
                        <h3 className="text-lg font-semibold text-gray-900 line-clamp-1">{actividad.titulo}</h3>
                        <Badge variant={estadoBadge.variant}>{estadoBadge.label}</Badge>
                      </div>
                      <p className="text-sm text-gray-600 mb-4 line-clamp-2">{actividad.descripcion}</p>
                      <div className="text-sm text-gray-500 space-y-1 mb-4">
                        <p>📍 {actividad.ubicacion.ciudad}</p>
                        <p>📅 {formatFechaHora(actividad.fecha)} hs</p>
                        <p>🏷️ {TIPO_ACTIVIDAD_LABEL[actividad.tipoActividad]}</p>
                      </div>
                    </CardBody>
                    <div className="bg-gray-50 px-5 py-4 border-t border-gray-100 flex justify-between items-center mt-auto">
                      <span className="text-xs font-medium text-gray-500">
                        {actividad.participantes.length} / {actividad.maximoParticipantes} participantes
                      </span>
                      <Button
                        variant="secondary"
                        className="text-xs px-3 py-1"
                        onClick={() => navigate(`/activities/${actividad.id}`)}
                      >
                        Ver detalles
                      </Button>
                    </div>
                  </Card>
                );
              })}

              {/* Tarjeta para crear nueva actividad */}
              <Card className="border-indigo-100 bg-indigo-50/30 flex flex-col justify-center">
                <CardBody className="flex flex-col items-center justify-center text-center py-8">
                  <span className="text-3xl mb-2">📥</span>
                  <h3 className="text-md font-semibold text-gray-900 mb-1">¿Tenés un plan?</h3>
                  <p className="text-sm text-gray-500 mb-4">Invitá a tus amigos y dejá que el sistema controle el clima.</p>
                  <Button variant="primary" className="text-sm" onClick={() => navigate('/activities/new')}>
                    + Nueva actividad
                  </Button>
                </CardBody>
              </Card>
            </div>
          ) : (
            <Card className="border-dashed border-2 border-gray-200">
              <CardBody className="text-center py-12">
                <span className="text-4xl mb-3 block">🗓️</span>
                <h3 className="text-lg font-semibold text-gray-900 mb-1">No hay actividades registradas todavía</h3>
                <p className="text-sm text-gray-500 mb-6 max-w-md mx-auto">
                  Sé el primero en organizar un evento deportivo, social o al aire libre.
                </p>
                <Button variant="primary" onClick={() => navigate('/activities/new')}>
                  Crear primera actividad
                </Button>
              </CardBody>
            </Card>
          )}
        </section>
      ) : (
        /* Presentación para visitantes no autenticados */
        <section>
          <h2 className="text-2xl font-bold text-gray-900 mb-6 text-center">¿Cómo funciona la plataforma?</h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <Card>
              <CardBody className="text-center py-6">
                <span className="text-4xl mb-3 block">⚽</span>
                <h3 className="text-lg font-semibold text-gray-900 mb-2">1. Organizá tu actividad</h3>
                <p className="text-sm text-gray-600">
                  Definí fecha, ubicación en el mapa, cupos mínimos y máximos, y el rango horario permitido para tu evento.
                </p>
              </CardBody>
            </Card>

            <Card>
              <CardBody className="text-center py-6">
                <span className="text-4xl mb-3 block">🌦️</span>
                <h3 className="text-lg font-semibold text-gray-900 mb-2">2. Monitoreo meteorológico</h3>
                <p className="text-sm text-gray-600">
                  El sistema evalúa el pronóstico periódicamente y envía alertas automáticas si el clima se vuelve desfavorable.
                </p>
              </CardBody>
            </Card>

            <Card>
              <CardBody className="text-center py-6">
                <span className="text-4xl mb-3 block">🗳️</span>
                <h3 className="text-lg font-semibold text-gray-900 mb-2">3. Votaciones democráticas</h3>
                <p className="text-sm text-gray-600">
                  Si llueve o hace mal tiempo, se proponen fechas alternativas automáticamente para que los participantes voten con quórum.
                </p>
              </CardBody>
            </Card>
          </div>
        </section>
      )}
    </div>
  );
};
