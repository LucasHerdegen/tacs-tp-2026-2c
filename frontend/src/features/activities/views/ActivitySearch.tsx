import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { mockActivities } from '../../../utils/mockData';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';

export const ActivitySearch: React.FC = () => {
  useDocumentTitle('Buscar Actividades');
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState('ALL');
  const navigate = useNavigate();

  // Simple filter logic
  const filteredActivities = mockActivities.filter(activity => {
    const matchesSearch = activity.title.toLowerCase().includes(searchTerm.toLowerCase()) || 
                          activity.location.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesType = filterType === 'ALL' || activity.type === filterType;
    return matchesSearch && matchesType;
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <h1 className="text-3xl font-bold text-gray-900">Buscar Actividades</h1>
      </div>

      {/* Filters Bar */}
      <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-100 flex flex-col md:flex-row gap-4">
        <div className="flex-1">
          <label className="label-text">Buscar por nombre o lugar</label>
          <input 
            type="text" 
            placeholder="Ej: Asado, Parque..." 
            className="input-field"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <div className="w-full md:w-48">
          <label className="label-text">Tipo de actividad</label>
          <select 
            className="input-field"
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
          >
            <option value="ALL">Todos</option>
            <option value="OUTDOOR">Aire libre</option>
            <option value="INDOOR">Techada</option>
            <option value="MIXED">Mixta</option>
          </select>
        </div>
        <div className="w-full md:w-48">
          <label className="label-text">Fecha</label>
          <input type="date" className="input-field" />
        </div>
      </div>

      {/* Results Grid */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {filteredActivities.length > 0 ? (
          filteredActivities.map(activity => (
            <Card key={activity.id} className="flex flex-col">
              <CardBody className="flex-grow">
                <div className="flex justify-between items-start mb-4">
                  <h3 className="text-lg font-semibold text-gray-900">{activity.title}</h3>
                  {activity.weatherCondition === 'IDEAL' && <Badge variant="success">Clima Ideal</Badge>}
                  {activity.weatherCondition === 'WARNING' && <Badge variant="warning">Alerta Lluvia</Badge>}
                  {activity.weatherCondition === 'BAD' && <Badge variant="error">Clima Malo</Badge>}
                </div>
                <p className="text-sm text-gray-600 mb-4 line-clamp-2">
                  {activity.description}
                </p>
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
          ))
        ) : (
          <div className="col-span-full text-center py-12 text-gray-500">
            No se encontraron actividades con esos filtros.
          </div>
        )}
      </div>
    </div>
  );
};
