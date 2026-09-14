import React, { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Card, CardBody } from '../../../components/ui/Card';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import type { ConfigurarCondicionesDto } from '../../../types/activity.types';
import { apiRequest, ApiError } from '../../../lib/api'; 
import { useAuth } from '../../auth/authContext';

export const WeatherConfig: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  useDocumentTitle('Configuración de Clima');

  // 1. Horas de anticipación
  const [horasAnticipacion, setHorasAnticipacion] = useState<number>(24);

  // 2. Rango de Reprogramación (Días y Horarios)
  const [diasReprogramacion, setDiasReprogramacion] = useState<number>(7);
  const [horaInicio, setHoraInicio] = useState<number>(12);
  const [horaFinal, setHoraFinal] = useState<number>(18);

  // 3. Reglas de Clima Aceptables (Permite null si no se ingresa nada)
  const [maxProbabilidadLluvia, setMaxProbabilidadLluvia] = useState<number | null>(null);
  const [minTemperatura, setMinTemperatura] = useState<number | null>(null);
  const [maxTemperatura, setMaxTemperatura] = useState<number | null>(null);
  const [maxViento, setMaxViento] = useState<number | null>(null);

  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const { token } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setMessage(null);

    // Validación básica de horario
    if (horaInicio >= horaFinal) {
      setMessage({
        type: 'error',
        text: 'La hora de inicio debe ser menor que la hora final.'
      });
      setIsSubmitting(false);
      return;
    }

    if (!token) {
      setMessage({
        type: 'error',
        text: 'No estás autenticado.'
      });
      setIsSubmitting(false);
      return;
    }

    // Armado del DTO
    const payload: ConfigurarCondicionesDto = {
      horasAnticipacion,
      rangoReprogramacion: {
        dias: diasReprogramacion,
        horaInicio,
        horaFinal
      },
      reglasClima: {
        maxProbabilidadLluvia:
          maxProbabilidadLluvia !== null
            ? Number(maxProbabilidadLluvia)
            : null,
        minTemperatura:
          minTemperatura !== null
            ? Number(minTemperatura)
            : null,
        maxTemperatura:
          maxTemperatura !== null
            ? Number(maxTemperatura)
            : null,
        maxViento:
          maxViento !== null
            ? Number(maxViento)
            : null
      }
    };

    try {
      console.log('Payload a enviar:', payload);

      await apiRequest(`/api/actividades/${id}/configuracion-clima`, {
        method: 'PATCH',
        token,
        body: JSON.stringify(payload),
      });

      setMessage({
        type: 'success',
        text: '¡Monitoreo de clima activado y configurado exitosamente!'
      });

      setTimeout(() => {
        navigate(`/activities/${id}`);
      }, 1500);

    } catch (err) {
      setMessage({
        type: 'error',
        text:
          err instanceof ApiError
            ? err.message
            : 'Error al guardar la configuración del clima.'
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <Link
          to={`/activities/${id}`}
          className="text-sm font-semibold text-indigo-600 hover:text-indigo-800 flex items-center gap-1 mb-2"
        >
          ← Volver al detalle
        </Link>
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          ⛅ Configurar Monitoreo de Clima
        </h1>
        <p className="text-gray-600 text-sm mt-1">
          Habilitá el chequeo periódico del clima y reglas de reprogramación para la actividad.
        </p>
      </div>

      {message && (
        <div className={`p-4 border rounded-xl text-sm font-medium flex items-center gap-2 ${
          message.type === 'success' 
            ? 'bg-green-50 border-green-200 text-green-800' 
            : 'bg-red-50 border-red-200 text-red-800'
        }`}>
          <span>{message.type === 'success' ? '✅' : '⚠️'}</span> {message.text}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <Card>
          <CardBody className="space-y-8">
            
            {/* 1. Anticipación de Aviso */}
            <div>
              <h3 className="text-md font-bold text-gray-900 mb-3 border-b pb-1">
                ⏱️ Anticipación del Aviso
              </h3>
              <div>
                <label className="label-text">Horas de anticipación para chequear el clima</label>
                <input
                  type="number"
                  min="1"
                  max="72"
                  value={horasAnticipacion}
                  onChange={(e) => setHorasAnticipacion(Number(e.target.value))}
                  className="input-field max-w-xs"
                  required
                />
                <p className="text-xs text-gray-500 mt-1">
                  Con cuántas horas de anticipación se te avisara si el clima va a estar mal.
                </p>
              </div>
            </div>

            {/* 2. Reglas y Límites del Clima (Opcionales / Nullable) */}
            <div>
              <h3 className="text-md font-bold text-gray-900 mb-3 border-b pb-1">
                🌡️ Condiciones Climáticas Aceptables (Opcionales)
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="label-text">Probabilidad Máxima de Lluvia (%)</label>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    value={maxProbabilidadLluvia ?? ''}
                    onChange={(e) => setMaxProbabilidadLluvia(e.target.value !== '' ? Number(e.target.value) : null)}
                    className="input-field"
                    placeholder="Sin límite"
                  />
                </div>

                <div>
                  <label className="label-text">Viento Máximo (km/h)</label>
                  <input
                    type="number"
                    min="0"
                    max="150"
                    value={maxViento ?? ''}
                    onChange={(e) => setMaxViento(e.target.value !== '' ? Number(e.target.value) : null)}
                    className="input-field"
                    placeholder="Sin límite"
                  />
                </div>

                <div>
                  <label className="label-text">Temperatura Mínima Aceptable (°C)</label>
                  <input
                    type="number"
                    min="-20"
                    max="50"
                    value={minTemperatura ?? ''}
                    onChange={(e) => setMinTemperatura(e.target.value !== '' ? Number(e.target.value) : null)}
                    className="input-field"
                    placeholder="Sin límite"
                  />
                </div>

                <div>
                  <label className="label-text">Temperatura Máxima Aceptable (°C)</label>
                  <input
                    type="number"
                    min="-20"
                    max="50"
                    value={maxTemperatura ?? ''}
                    onChange={(e) => setMaxTemperatura(e.target.value !== '' ? Number(e.target.value) : null)}
                    className="input-field"
                    placeholder="Sin límite"
                  />
                </div>
              </div>
            </div>

            {/* 3. Ventana y Rango de Reprogramación */}
            <div>
              <h3 className="text-md font-bold text-gray-900 mb-3 border-b pb-1">
                📅 Rango para Reprogramar
              </h3>
              
              <div className="space-y-4">
                <div>
                  <label className="label-text">Rango Máximo de Días</label>
                  <input
                    type="number"
                    min="1"
                    max="30"
                    value={diasReprogramacion}
                    onChange={(e) => setDiasReprogramacion(Number(e.target.value))}
                    className="input-field max-w-xs"
                    required
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="label-text">Hora de Inicio Permitida (0 a 23 hs)</label>
                    <input
                      type="number"
                      min="0"
                      max="23"
                      value={horaInicio}
                      onChange={(e) => setHoraInicio(Number(e.target.value))}
                      className="input-field"
                      required
                    />
                  </div>

                  <div>
                    <label className="label-text">Hora Final Permitida (0 a 23 hs)</label>
                    <input
                      type="number"
                      min="0"
                      max="23"
                      value={horaFinal}
                      onChange={(e) => setHoraFinal(Number(e.target.value))}
                      className="input-field"
                      required
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Botones de acción */}
            <div className="pt-4 flex justify-end gap-3 border-t border-gray-100">
              <Link to={`/activities/${id}`}>
                <Button variant="secondary" type="button" disabled={isSubmitting}>
                  Cancelar
                </Button>
              </Link>
              <Button variant="primary" type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Guardando...' : 'Guardar Configuracion'}
              </Button>
            </div>

          </CardBody>
        </Card>
      </form>
    </div>
  );
};