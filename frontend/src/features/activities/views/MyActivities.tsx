import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { ApiError } from '../../../lib/api';
import { formatFechaHora } from '../../../utils/dateTime';
import { useAuth } from '../../auth/authContext';
import { votingApi } from '../../voting/votingApi';
import type { VotacionDto } from '../../voting/types';
import { activitiesApi } from '../activitiesApi';
import { ESTADO_BADGE } from '../activityLabels';
import { canManageActivity } from '../activityPermissions';
import type { Actividad, TipoActividad } from '../types';

const TIPO_ACTIVIDAD_LABEL: Record<TipoActividad, string> = {
  AIRE_LIBRE: 'Aire libre',
  TECHADA: 'Techada',
  MIXTA: 'Mixta',
};

const ActivityCard: React.FC<{ actividad: Actividad }> = ({ actividad }) => {
  const navigate = useNavigate();
  const estadoBadge = ESTADO_BADGE[actividad.estadoActividad];

  return (
    <Card className="flex flex-col">
      <CardBody className="flex-grow">
        <div className="flex justify-between items-start mb-4">
          <h3 className="text-lg font-semibold text-gray-900">{actividad.titulo}</h3>
          <Badge variant={estadoBadge.variant}>{estadoBadge.label}</Badge>
        </div>
        <p className="text-sm text-gray-600 mb-4 line-clamp-2">{actividad.descripcion}</p>
        <div className="text-sm text-gray-500 space-y-1 mb-4">
          <p>📍 {actividad.ubicacion.barrio}</p>
          <p>📅 {formatFechaHora(actividad.fecha)} hs</p>
          <p>🏷️ {TIPO_ACTIVIDAD_LABEL[actividad.tipoActividad]}</p>
        </div>
      </CardBody>
      <div className="bg-gray-50 px-5 py-4 border-t border-gray-100 flex justify-between items-center mt-auto">
        <span className="text-xs font-medium text-gray-500">
          {actividad.participantes.length} / {actividad.maximoParticipantes} participantes
        </span>
        <Button variant="secondary" className="text-xs px-3 py-1" onClick={() => navigate(`/activities/${actividad.id}`)}>
          Ver detalles
        </Button>
      </div>
    </Card>
  );
};

export const MyActivities: React.FC = () => {
  useDocumentTitle('Mis Actividades');
  const { user, token } = useAuth();
  const navigate = useNavigate();

  const [organizadas, setOrganizadas] = useState<Actividad[]>([]);
  const [sumadas, setSumadas] = useState<Actividad[]>([]);
  const [votacionesAbiertas, setVotacionesAbiertas] = useState<VotacionDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token || !user) return;
    let ignore = false;

    Promise.all([
      activitiesApi.misActividades(true, token),
      activitiesApi.misActividades(false, token),
      votingApi.listar(true, token),
    ])
      .then(([propias, participadas, abiertas]) => {
        if (ignore) return;
        setOrganizadas(propias);
        // El backend agrega al organizador como participante, así que "participadas" incluye las propias.
        setSumadas(participadas.filter((actividad) => !canManageActivity(actividad, user)));
        setVotacionesAbiertas(abiertas);
      })
      .catch((requestError: unknown) => {
        if (ignore) return;
        setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cargar tus actividades.');
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [token, user]);

  if (loading) {
    return <p className="text-center py-20 text-gray-500">Cargando tus actividades…</p>;
  }

  return (
    <div className="space-y-10">
      <h1 className="text-3xl font-bold text-gray-900">Mis Actividades</h1>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>
      )}

      <section>
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Actividades que organizo</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {organizadas.length > 0 ? (
            organizadas.map((actividad) => <ActivityCard key={actividad.id} actividad={actividad} />)
          ) : (
            <div className="col-span-full text-center py-12 text-gray-500">
              Todavía no organizaste ninguna actividad.
            </div>
          )}
        </div>
      </section>

      <section>
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Actividades a las que me sumé</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {sumadas.length > 0 ? (
            sumadas.map((actividad) => <ActivityCard key={actividad.id} actividad={actividad} />)
          ) : (
            <div className="col-span-full text-center py-12 text-gray-500">
              Todavía no te sumaste a ninguna actividad.
            </div>
          )}
        </div>
      </section>

      <section>
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Votaciones abiertas</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {votacionesAbiertas.length > 0 ? (
            votacionesAbiertas.map((votacion) => (
              <Card key={votacion.id} className="flex flex-col">
                <CardBody className="grow">
                  <div className="flex justify-between items-start mb-4">
                    <h3 className="text-lg font-semibold text-gray-900">{votacion.actividadDto.titulo}</h3>
                    <Badge variant="warning">Votación abierta</Badge>
                  </div>
                  <p className="text-sm text-gray-600 mb-1">
                    Cierra el {formatFechaHora(votacion.fechaLimite)} hs
                  </p>
                  <p className="text-sm text-gray-600">
                    {votacion.alternativasDtos.length} alternativas
                  </p>
                </CardBody>
                <div className="bg-gray-50 px-5 py-4 border-t border-gray-100 flex justify-end mt-auto">
                  <Button
                    variant="secondary"
                    className="text-xs px-3 py-1"
                    onClick={() => navigate(`/activities/${votacion.actividadDto.id}`)}
                  >
                    Ver actividad
                  </Button>
                </div>
              </Card>
            ))
          ) : (
            <div className="col-span-full text-center py-12 text-gray-500">
              No tenés votaciones abiertas.
            </div>
          )}
        </div>
      </section>
    </div>
  );
};
