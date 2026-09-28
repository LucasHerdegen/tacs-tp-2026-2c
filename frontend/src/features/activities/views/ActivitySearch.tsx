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

  const [appliedSearchTerm, setAppliedSearchTerm] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState('');
  const [filterFechaInicio, setFilterFechaInicio] = useState('');
  const [filterFechaFin, setFilterFechaFin] = useState('');
  const [filterEstado, setFilterEstado] = useState('');
  
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);

  const [activities, setActivities] = useState<Actividad[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const navigate = useNavigate();
  const { token } = useAuth();

  // Reset pagination when filters change
  useEffect(() => {
    setPage(0);
  }, [filterType, appliedSearchTerm, filterFechaInicio, filterFechaFin, filterEstado, pageSize]);

  useEffect(() => {
    const buscarActividades = async () => {
      if (!token) {
        setError('No estás autenticado.');
        return;
      }

      if (filterFechaInicio && filterFechaFin && filterFechaInicio > filterFechaFin) {
        setError('La fecha de inicio no puede ser posterior a la de fin.');
        setActivities([]);
        setTotalPages(0);
        return;
      }

      setIsLoading(true);
      setError(null);

      try {
        const data = await activitiesApi.buscar(
          filterType || null,
          appliedSearchTerm || null,
          filterFechaInicio || null,
          filterFechaFin || null,
          filterEstado || null,
          page,
          pageSize,
          token,
        );

        setActivities(data.content || []);
        setTotalPages(data.totalPages || 0);
        
        // Safety: if the current page is out of bounds due to a backend change, jump back
        if (data.totalPages > 0 && page >= data.totalPages) {
          setPage(data.totalPages - 1);
        }
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
  }, [filterType, appliedSearchTerm, filterFechaInicio, filterFechaFin, filterEstado, page, pageSize, token]);

  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <h1 className="text-3xl font-bold text-gray-900">
          Buscar Actividades
        </h1>
      </div>

      <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-100 grid grid-cols-1 md:grid-cols-4 lg:grid-cols-6 gap-4">
        <div className="md:col-span-2 lg:col-span-2">
          <label className="label-text">
            Buscar por nombre o lugar
          </label>

          <input
            type="text"
            placeholder="Ej: Asado, Parque..."
            className="input-field"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setAppliedSearchTerm(searchTerm);
              }
            }}
            onBlur={() => {
              setAppliedSearchTerm(searchTerm);
            }}
          />
        </div>

        <div className="md:col-span-1 lg:col-span-1">
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

        <div className="md:col-span-1 lg:col-span-1">
          <label className="label-text">
            Estado
          </label>

          <select
            className="input-field"
            value={filterEstado}
            onChange={(e) => setFilterEstado(e.target.value)}
          >
            <option value="">Todos</option>
            <option value="PROPUESTA">Propuesta</option>
            <option value="CONFIRMADA">Confirmada</option>
            <option value="REPROGRAMADA">Reprogramada</option>
            <option value="FINALIZADA">Finalizada</option>
            <option value="CANCELADA">Cancelada</option>
          </select>
        </div>

        <div className="md:col-span-4 lg:col-span-2 flex gap-2">
          <div>
            <label className="label-text">
              Fecha desde
            </label>
            <input
              type="date"
              className="input-field"
              value={filterFechaInicio}
              onChange={(e) => setFilterFechaInicio(e.target.value)}
            />
          </div>
          <div>
            <label className="label-text">
              Fecha hasta
            </label>
            <input
              type="date"
              className="input-field"
              value={filterFechaFin}
              onChange={(e) => setFilterFechaFin(e.target.value)}
            />
          </div>
        </div>
      </div>

      <div className="flex justify-between items-center bg-white p-4 rounded-xl shadow-sm border border-gray-100">
        <div className="flex items-center gap-2">
          <label className="text-sm text-gray-600 font-medium">Resultados por página:</label>
          <select 
            className="border-gray-300 rounded-md text-sm py-1 px-2 focus:ring-indigo-500 focus:border-indigo-500"
            value={pageSize}
            onChange={(e) => setPageSize(Number(e.target.value))}
          >
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={50}>50</option>
          </select>
        </div>
        
        {totalPages > 0 && (
          <div className="flex items-center gap-2 text-sm text-gray-600">
            Página 
            <input 
              type="number" 
              min={1} 
              max={totalPages}
              value={page + 1}
              onChange={(e) => {
                const val = parseInt(e.target.value, 10);
                if (!isNaN(val)) {
                  handlePageChange(val - 1);
                }
              }}
              className="w-16 border-gray-300 rounded-md py-1 px-2 text-center focus:ring-indigo-500 focus:border-indigo-500"
            />
            de {totalPages}
          </div>
        )}
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
        <>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {activities.length > 0 ? (
              activities.map((activity) => (
                <Card key={activity.id} className="flex flex-col">
                  <CardBody className="flex-grow">
                    <div className="flex justify-between items-start mb-4">
                      <h3 className="text-lg font-semibold text-gray-900">
                        {activity.titulo}
                      </h3>

                      {activity.estadoActividad === 'PROPUESTA' && (
                        <Badge variant="info">
                          Propuesta
                        </Badge>
                      )}

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

                      {activity.estadoActividad === 'FINALIZADA' && (
                        <Badge variant="neutral">
                          Finalizada
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

          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-4 mt-8">
              <Button
                variant="secondary"
                disabled={page <= 0}
                onClick={() => handlePageChange(page - 1)}
              >
                Anterior
              </Button>
              <Button
                variant="secondary"
                disabled={page >= totalPages - 1}
                onClick={() => handlePageChange(page + 1)}
              >
                Siguiente
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
};
