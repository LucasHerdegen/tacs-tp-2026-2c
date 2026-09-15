import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { useAuth } from '../../auth/authContext';
import { activitiesApi } from '../activitiesApi';
import type { Actividad } from '../types';
import { ApiError } from '../../../lib/api';

export const ActivitySearch: React.FC = () => {
  useDocumentTitle('Buscar Actividades');

  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState('');
  const [filterDate, setFilterDate] = useState('');
  const [activities, setActivities] = useState<Actividad[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const navigate = useNavigate();
  const { token } = useAuth();

  useEffect(() => {
    const buscarActividades = async () => {
      if (!token) {
        setError('No estás autenticado.');
        return;
      }

      setIsLoading(true);
      setError(null);

      try {
        const data = await activitiesApi.buscar(
          filterType || null,
          searchTerm || null,
          filterDate || null,
          token,
        );

        setActivities(data);
      } catch (err) {
        setError(
          err instanceof ApiError
            ? err.message
            : 'Error al buscar actividades.'
        );
      } finally {
        setIsLoading(false);
      }
    };

    buscarActividades();
  }, [filterType, searchTerm, filterDate, token]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <h1 className="text-3xl font-bold text-gray-900">
          Buscar Actividades
        </h1>
      </div>

      <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-100 flex flex-col md:flex-row gap-4">
        <div className="flex-1">
          <label className="label-text">
            Buscar por nombre o lugar
          </label>

          <input
            type="text"
            placeholder="Ej: Asado, Parque..."
            className="input-field"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div className="w-full md:w-48">
          <label className="label-text">
            Tipo de actividad
          </label>

          <select
            className="input-field"
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
          >
            <option value="">Todos</option>
            <option value="AIRE_LIBRE">Aire libre</option>
            <option value="TECHADA">Techada</option>
            <option value="MIXTA">Mixta</option>
          </select>
        </div>

        <div className="w-full md:w-48">
          <label className="label-text">
            Fecha
          </label>

          <input
            type="date"
            className="input-field"
            value={filterDate}
            onChange={(e) => setFilterDate(e.target.value)}
          />
        </div>
      </div>

      {isLoading ? (
        <div className="text-center py-12 text-gray-500">
          Buscando actividades...
        </div>
      ) : error ? (
        <div className="text-center py-12 text-red-600">
          {error}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {activities.length > 0 ? (
            activities.map((activity) => (
              <Card key={activity.id} className="flex flex-col">
                <CardBody className="flex-grow">
                  <div className="flex justify-between items-start mb-4">
                    <h3 className="text-lg font-semibold text-gray-900">
                      {activity.titulo}
                    </h3>

                    {activity.estadoActividad === 'CONFIRMADA' && (
                      <Badge variant="success">
                        Confirmada
                      </Badge>
                    )}

                    {activity.estadoActividad === 'REPROGRAMADA' && (
                      <Badge variant="warning">
                        Reprogramada
                      </Badge>
                    )}

                    {activity.estadoActividad === 'CANCELADA' && (
                      <Badge variant="error">
                        Cancelada
                      </Badge>
                    )}
                  </div>

                  <p className="text-sm text-gray-600 mb-4 line-clamp-2">
                    {activity.descripcion}
                  </p>

                  <div className="text-sm text-gray-500 space-y-1 mb-4">
                    <p>
                      📍 {activity.ubicacion.barrio}
                    </p>

                    <p>
                      📅{' '}
                      {new Date(activity.fecha).toLocaleString('es-AR', {
                        dateStyle: 'medium',
                        timeStyle: 'short'
                      })}{' '}
                      hs
                    </p>

                    <p>
                      🏷️ {activity.tipoActividad}
                    </p>
                  </div>
                </CardBody>

                <div className="bg-gray-50 px-5 py-4 border-t border-gray-100 flex justify-between items-center mt-auto">
                  <span className="text-xs font-medium text-gray-500">
                    {activity.participantes.length} / {activity.maximoParticipantes} participantes
                  </span>

                  <Button
                    variant="secondary"
                    className="text-xs px-3 py-1"
                    onClick={() =>
                      navigate(`/activities/${activity.id}`)
                    }
                  >
                    Ver detalles
                  </Button>
                </div>
              </Card>
            ))
          ) : (
            <div className="col-span-full text-center py-12 text-gray-500">
              No se encontraron actividades con esos filtros.
            </div>
          )}
        </div>
      )}
    </div>
  );
};
