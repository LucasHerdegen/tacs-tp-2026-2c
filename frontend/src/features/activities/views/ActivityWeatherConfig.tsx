import React, { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { mockActivities } from '../../../utils/mockData';
import { Card, CardBody } from '../../../components/ui/Card';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';

export const WeatherConfig: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const activity = mockActivities.find(a => a.id === id);

  useDocumentTitle(
    activity ? `Configuración de Clima - ${activity.title}` : 'Configurar Clima'
  );

  const [rainThreshold, setRainThreshold] = useState<number>(50); 
  const [maxWindSpeed, setMaxWindSpeed] = useState<number>(30);
  const [autoCancel, setAutoCancel] = useState<boolean>(false);
  const [allowPoll, setAllowPoll] = useState<boolean>(true);
  const [isSaved, setIsSaved] = useState<boolean>(false);

  if (!activity) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold text-gray-900">Actividad no encontrada</h2>
        <Link to="/activities" className="text-indigo-600 hover:underline mt-4 inline-block font-medium">
          Volver al buscador
        </Link>
      </div>
    );
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaved(true);
    setTimeout(() => {
      navigate(`/activities/${activity.id}`);
    }, 1500);
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Botón Volver y Cabecera */}
      <div>
        <Link
          to={`/activities/${activity.id}`}
          className="text-sm font-semibold text-indigo-600 hover:text-indigo-800 flex items-center gap-1 mb-2"
        >
          Volver a la actividad
        </Link>
        <h1 className="text-2xl font-bold text-gray-900">
          Configuración Climática de la Actividad
        </h1>
        <p className="text-gray-600 text-sm mt-1">
          Ajustá los umbrales meteorológicos para <span className="font-semibold text-gray-800">{activity.title}</span>.
        </p>
      </div>

      {/* Alerta de guardado exitoso */}
      {isSaved && (
        <div className="p-4 bg-green-50 border border-green-200 text-green-800 rounded-xl text-sm font-medium flex items-center gap-2">
          <span>OK</span> Configuración de clima actualizada correctamente. Redirigiendo...
        </div>
      )}

      {/* Formulario de Configuración */}
      <form onSubmit={handleSubmit}>
        <Card className="space-y-6">
          <CardBody className="space-y-6">
            
            {/* Umbral de Lluvia */}
            <div>
              <label className="label-text flex justify-between items-center">
                <span>Porcentaje de lluvia máximo tolerado</span>
                <span className="font-bold text-indigo-600 text-base">{rainThreshold}%</span>
              </label>
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                value={rainThreshold}
                onChange={(e) => setRainThreshold(Number(e.target.value))}
                className="w-full accent-indigo-600 cursor-pointer h-2 bg-gray-200 rounded-lg"
              />
              <p className="text-xs text-gray-500 mt-1">
                Si la probabilidad de lluvia supera este valor, se notificará a los participantes.
              </p>
            </div>

            {/* Umbral de Viento */}
            <div>
              <label className="label-text flex justify-between items-center">
                <span>Velocidad de viento máxima tolerada</span>
                <span className="font-bold text-indigo-600 text-base">{maxWindSpeed} km/h</span>
              </label>
              <input
                type="range"
                min="5"
                max="80"
                step="5"
                value={maxWindSpeed}
                onChange={(e) => setMaxWindSpeed(Number(e.target.value))}
                className="w-full accent-indigo-600 cursor-pointer h-2 bg-gray-200 rounded-lg"
              />
              <p className="text-xs text-gray-500 mt-1">
                Recomendado para actividades al aire libre (OUTDOOR).
              </p>
            </div>

            <hr className="border-gray-100" />

            {/* Toggles / Opciones de Comportamiento */}
            <div className="space-y-4">
              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={allowPoll}
                  onChange={(e) => setAllowPoll(e.target.checked)}
                  className="mt-1 h-4 w-4 text-indigo-600 focus:ring-indigo-500 rounded border-gray-300"
                />
                <div>
                  <span className="font-semibold text-gray-900 text-sm block">
                    Habilitar votación automática de reprogramación
                  </span>
                  <span className="text-xs text-gray-500">
                    Si el clima empeora, los participantes recibirán una encuesta para elegir fecha alternativa.
                  </span>
                </div>
              </label>

              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={autoCancel}
                  onChange={(e) => setAutoCancel(e.target.checked)}
                  className="mt-1 h-4 w-4 text-indigo-600 focus:ring-indigo-500 rounded border-gray-300"
                />
                <div>
                  <span className="font-semibold text-gray-900 text-sm block">
                    Cancelar actividad automáticamente ante alerta roja
                  </span>
                  <span className="text-xs text-gray-500">
                    Cancela la actividad 3 horas antes si las condiciones meteorológicas son extremas.
                  </span>
                </div>
              </label>
            </div>

            {/* Acciones */}
            <div className="pt-4 flex justify-end gap-3 border-t border-gray-100">
              <Link to={`/activities/${activity.id}`}>
                <Button variant="secondary" type="button">
                  Cancelar
                </Button>
              </Link>
              <Button variant="primary" type="submit">
                Guardar Configuración
              </Button>
            </div>

          </CardBody>
        </Card>
      </form>
    </div>
  );
};