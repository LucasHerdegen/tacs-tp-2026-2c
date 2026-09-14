import React from 'react';
import { useNavigate } from 'react-router-dom';
import { mockActivities, mockVotaciones, type Activity, type EstadoActividad } from '../../../utils/mockData';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge, type BadgeVariant } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';

const ESTADO_BADGE: Record<EstadoActividad, { variant: BadgeVariant; label: string }> = {
  PROPUESTA: { variant: 'info', label: 'Propuesta' },
  CONFIRMADA: { variant: 'success', label: 'Confirmada' },
  REPROGRAMADA: { variant: 'warning', label: 'Reprogramada' },
  CANCELADA: { variant: 'error', label: 'Cancelada' },
  FINALIZADA: { variant: 'neutral', label: 'Finalizada' },
};

const ActivityCard: React.FC<{ activity: Activity }> = ({ activity }) => {
  const navigate = useNavigate();
  const estadoBadge = ESTADO_BADGE[activity.estado];

  return (
    <Card className="flex flex-col">
      <CardBody className="flex-grow">
        <div className="flex justify-between items-start mb-4">
          <h3 className="text-lg font-semibold text-gray-900">{activity.title}</h3>
          <Badge variant={estadoBadge.variant}>{estadoBadge.label}</Badge>
        </div>
        <p className="text-sm text-gray-600 mb-4 line-clamp-2">{activity.description}</p>
        <div className="text-sm text-gray-500 space-y-1 mb-4">
          <p>📍 {activity.location}</p>
          <p>📅 {new Date(activity.date).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' })} hs</p>
          <p>🏷️ {activity.type}</p>
        </div>
      </CardBody>
      <div className="bg-gray-50 px-5 py-4 border-t border-gray-100 flex justify-between items-center mt-auto">
        <span className="text-xs font-medium text-gray-500">
          {activity.currentParticipants} / {activity.maxParticipants} participantes
        </span>
        <Button variant="secondary" className="text-xs px-3 py-1" onClick={() => navigate(`/activities/${activity.id}`)}>
          Ver detalles
        </Button>
      </div>
    </Card>
  );
};

export const MyActivities: React.FC = () => {
  useDocumentTitle('Mis Actividades');
  const navigate = useNavigate();

  const organizedActivities = mockActivities.filter(a => a.isOrganizer);
  const joinedActivities = mockActivities.filter(a => a.isJoined && !a.isOrganizer);
  const openVotaciones = mockVotaciones.filter(v => v.abierta);

  return (
    <div className="space-y-10">
      <h1 className="text-3xl font-bold text-gray-900">Mis Actividades</h1>

      <section>
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Actividades que organizo</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {organizedActivities.length > 0 ? (
            organizedActivities.map(activity => <ActivityCard key={activity.id} activity={activity} />)
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
          {joinedActivities.length > 0 ? (
            joinedActivities.map(activity => <ActivityCard key={activity.id} activity={activity} />)
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
          {openVotaciones.length > 0 ? (
            openVotaciones.map(votacion => (
              <Card key={votacion.id} className="flex flex-col">
                <CardBody className="grow">
                  <div className="flex justify-between items-start mb-4">
                    <h3 className="text-lg font-semibold text-gray-900">{votacion.actividadTitulo}</h3>
                    <Badge variant="warning">Votación abierta</Badge>
                  </div>
                  <p className="text-sm text-gray-600 mb-1">
                    Cierra el {new Date(votacion.fechaLimite).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' })} hs
                  </p>
                  <p className="text-sm text-gray-600">
                    {votacion.alternativas.length} alternativas
                  </p>
                </CardBody>
                <div className="bg-gray-50 px-5 py-4 border-t border-gray-100 flex justify-end mt-auto">
                  <Button
                    variant="secondary"
                    className="text-xs px-3 py-1"
                    onClick={() => navigate(`/activities/${votacion.actividadId}`)}
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
