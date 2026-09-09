import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { mockActivities } from '../../../utils/mockData';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';

export const ActivityDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  
  // En la vida real usaríamos un useEffect para buscar en el backend
  const initialActivity = mockActivities.find(a => a.id === id);
  
  // Usamos estado para simular que nos sumamos o bajamos (US 10)
  const [activity, setActivity] = useState(initialActivity);

  useDocumentTitle(activity ? activity.title : 'Detalle de Actividad');

  if (!activity) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold text-gray-900">Actividad no encontrada</h2>
        <Link to="/activities" className="text-indigo-600 hover:underline mt-4 inline-block">Volver al buscador</Link>
      </div>
    );
  }

  const isFull = activity.currentParticipants >= activity.maxParticipants;

  const handleJoinLeave = () => {
    setActivity(prev => {
      if (!prev) return prev;
      return {
        ...prev,
        isJoined: !prev.isJoined,
        currentParticipants: prev.isJoined ? prev.currentParticipants - 1 : prev.currentParticipants + 1
      };
    });
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      
      {/* Header Info */}
      <div className="bg-white p-6 sm:p-8 rounded-2xl shadow-sm border border-gray-100 relative overflow-hidden">
        <div className="absolute top-0 right-0 p-4">
          <Badge variant="info" className="px-3 py-1">{activity.type}</Badge>
        </div>
        <h1 className="text-3xl font-bold text-gray-900 mb-2">{activity.title}</h1>
        <p className="text-gray-600 text-lg mb-6">{activity.description}</p>
        
        <div className="flex flex-col sm:flex-row gap-6 text-gray-700">
          <div className="flex items-center gap-2">
            <span className="text-xl">📍</span>
            <div>
              <p className="text-xs text-gray-500 uppercase font-semibold tracking-wider">Ubicación</p>
              <p className="font-medium">{activity.location}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xl">📅</span>
            <div>
              <p className="text-xs text-gray-500 uppercase font-semibold tracking-wider">Fecha y Hora</p>
              <p className="font-medium">{new Date(activity.date).toLocaleString('es-AR', { dateStyle: 'long', timeStyle: 'short' })} hs</p>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        
        {/* Columna Izquierda: Clima (US 11) */}
        <div className="md:col-span-2 space-y-6">
          <Card>
            <CardBody>
              <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
                ⛅ Estado del Clima
              </h2>
              
              {!activity.isJoined ? (
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-6 text-center">
                  <p className="text-gray-600 mb-2">Sumate a la actividad para recibir alertas tempranas si el pronóstico empeora.</p>
                  <p className="text-sm text-gray-500 italic">Podrás votar opciones de reprogramación si las condiciones no son óptimas.</p>
                </div>
              ) : (
                <div className={`rounded-lg p-6 border ${activity.weatherCondition === 'IDEAL' ? 'bg-green-50 border-green-200' : activity.weatherCondition === 'WARNING' ? 'bg-yellow-50 border-yellow-200' : 'bg-red-50 border-red-200'}`}>
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className={`text-lg font-bold ${activity.weatherCondition === 'IDEAL' ? 'text-green-800' : activity.weatherCondition === 'WARNING' ? 'text-yellow-800' : 'text-red-800'}`}>
                        {activity.weatherCondition === 'IDEAL' ? 'Pronóstico Ideal' : activity.weatherCondition === 'WARNING' ? 'Probabilidad de Lluvias' : 'Condiciones Desfavorables'}
                      </h3>
                      <p className={`text-sm mt-1 ${activity.weatherCondition === 'IDEAL' ? 'text-green-700' : activity.weatherCondition === 'WARNING' ? 'text-yellow-700' : 'text-red-700'}`}>
                        {activity.weatherCondition === 'IDEAL' ? 'Todo marcha perfecto para este evento.' : activity.weatherCondition === 'WARNING' ? 'Mantente atento a las notificaciones por si se requiere votación.' : 'Es muy probable que el organizador deba reprogramar.'}
                      </p>
                    </div>
                    <div className="text-4xl">
                      {activity.weatherCondition === 'IDEAL' ? '☀️' : activity.weatherCondition === 'WARNING' ? '🌦️' : '⛈️'}
                    </div>
                  </div>
                  <div className="mt-4 flex gap-4 text-sm font-medium">
                    <span>Temp: 24°C</span>
                    <span>Viento: 12 km/h</span>
                    <span>Lluvia: {activity.weatherCondition === 'IDEAL' ? '0%' : activity.weatherCondition === 'WARNING' ? '60%' : '90%'}</span>
                  </div>
                </div>
              )}
            </CardBody>
          </Card>
        </div>

        {/* Columna Derecha: Panel de Participación (US 10) */}
        <div>
          <Card className="sticky top-6">
            <CardBody>
              <h3 className="text-lg font-bold text-gray-900 mb-2">Cupos</h3>
              <div className="w-full bg-gray-200 rounded-full h-2.5 mb-2">
                <div 
                  className={`h-2.5 rounded-full ${isFull ? 'bg-red-500' : 'bg-indigo-600'}`} 
                  style={{ width: `${(activity.currentParticipants / activity.maxParticipants) * 100}%` }}
                ></div>
              </div>
              <p className="text-sm text-gray-600 mb-6 font-medium">
                {activity.currentParticipants} de {activity.maxParticipants} lugares ocupados
              </p>

              {activity.isJoined ? (
                <Button 
                  variant="danger"
                  onClick={handleJoinLeave}
                  className="w-full text-base py-3"
                >
                  Bajarme de la actividad
                </Button>
              ) : (
                <Button 
                  variant="primary"
                  onClick={handleJoinLeave}
                  disabled={isFull}
                  className="w-full text-base py-3"
                >
                  {isFull ? 'Actividad Llena' : 'Sumarme a la actividad'}
                </Button>
              )}
              
              <p className="text-xs text-gray-500 mt-4 text-center">
                Mínimo requerido para confirmar: {activity.minParticipants}
              </p>
            </CardBody>
          </Card>
        </div>

      </div>
    </div>
  );
};
