import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Card, CardBody } from '../../../components/ui/Card';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import type { ConfigurarCondiciones, TipoActividad } from '../types';
import { ApiError } from '../../../lib/api';
import { useAuth } from '../../auth/authContext';
import { activitiesApi } from '../activitiesApi';

interface WeatherPreset {
  nombre: string;
  descripcion: string;
  badge: string;
  horasAnticipacion: number;
  diasReprogramacion: number;
  horaInicio: number;
  horaFinal: number;
  maxProbabilidadLluvia: number;
  maxViento: number | '';
  minTemperatura: number | '';
  maxTemperatura: number | '';
}

const PRESETS_POR_TIPO: Record<TipoActividad, WeatherPreset> = {
  AIRE_LIBRE: {
    nombre: 'Aire Libre',
    descripcion: 'Eventos al aire libre (ej: fútbol, running, picnic). Tolerancia baja a lluvia y viento moderado.',
    badge: '🌳 AIRE LIBRE',
    horasAnticipacion: 24,
    diasReprogramacion: 3,
    horaInicio: 10,
    horaFinal: 18,
    maxProbabilidadLluvia: 30,
    maxViento: 35,
    minTemperatura: 12,
    maxTemperatura: 32,
  },
  TECHADA: {
    nombre: 'Techada / Interior',
    descripcion: 'Espacios cerrados (ej: gimnasio, cancha cubierta). La lluvia no afecta el evento y el viento no tiene restricción.',
    badge: '🏠 TECHADA',
    horasAnticipacion: 12,
    diasReprogramacion: 3,
    horaInicio: 10,
    horaFinal: 22,
    maxProbabilidadLluvia: 100,
    maxViento: '',
    minTemperatura: 10,
    maxTemperatura: 35,
  },
  MIXTA: {
    nombre: 'Mixta',
    descripcion: 'Espacios semicubiertos o con áreas combinadas. Tolerancia intermedia a lluvia y viento.',
    badge: '⛅ MIXTA',
    horasAnticipacion: 24,
    diasReprogramacion: 3,
    horaInicio: 10,
    horaFinal: 20,
    maxProbabilidadLluvia: 40,
    maxViento: 45,
    minTemperatura: 12,
    maxTemperatura: 32,
  },
};

export const WeatherConfig: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  useDocumentTitle('Configuración de Clima');

  // Metadata de la actividad
  const [tipoActividad, setTipoActividad] = useState<TipoActividad | null>(null);
  const [duracionEstimada, setDuracionEstimada] = useState<number>(0);
  const [actividadTitulo, setActividadTitulo] = useState<string>('');

  // Valores de formulario con soporte de valor vacío (number | '')
  const [horasAnticipacion, setHorasAnticipacion] = useState<number | ''>(24);
  const [diasReprogramacion, setDiasReprogramacion] = useState<number | ''>(3);
  const [horaInicio, setHoraInicio] = useState<number | ''>(12);
  const [horaFinal, setHoraFinal] = useState<number | ''>(18);

  const [maxProbabilidadLluvia, setMaxProbabilidadLluvia] = useState<number | ''>(50);
  const [minTemperatura, setMinTemperatura] = useState<number | ''>('');
  const [maxTemperatura, setMaxTemperatura] = useState<number | ''>('');
  const [maxViento, setMaxViento] = useState<number | ''>('');

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [showConfirmModal, setShowConfirmModal] = useState<boolean>(false);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const { token } = useAuth();

  useEffect(() => {
    const cargarConfiguracion = async () => {
      if (!token || !id) {
        setIsLoading(false);
        return;
      }

      try {
        const actividad = await activitiesApi.obtener(id, token);

        if (!actividad) {
          setMessage({
            type: 'error',
            text: 'No se encontró la actividad.'
          });
          return;
        }

        if (actividad.tipoActividad) {
          setTipoActividad(actividad.tipoActividad);
        }
        if (actividad.duracionEstimada != null) {
          setDuracionEstimada(actividad.duracionEstimada);
        }
        if (actividad.titulo) {
          setActividadTitulo(actividad.titulo);
        }

        if (actividad.horasAnticipacion != null) {
          setHorasAnticipacion(actividad.horasAnticipacion);
        }

        if (actividad.rangoReprogramacion) {
          setDiasReprogramacion(actividad.rangoReprogramacion.dias ?? 3);
          setHoraInicio(actividad.rangoReprogramacion.horaInicio ?? 12);
          setHoraFinal(actividad.rangoReprogramacion.horaFinal ?? 18);
        }

        if (actividad.reglasClima) {
          if (actividad.reglasClima.maxProbabilidadLluvia != null) {
            setMaxProbabilidadLluvia(actividad.reglasClima.maxProbabilidadLluvia);
          }
          setMinTemperatura(
            actividad.reglasClima.minTemperatura != null ? actividad.reglasClima.minTemperatura : ''
          );
          setMaxTemperatura(
            actividad.reglasClima.maxTemperatura != null
              ? actividad.reglasClima.maxTemperatura
              : ''
          );
          setMaxViento(
            actividad.reglasClima.maxViento != null
              ? actividad.reglasClima.maxViento
              : ''
          );
        } else {
          // Si no tiene reglas climáticas previas, dejar opcionales vacías ('No definido')
          setMinTemperatura('');
          setMaxTemperatura('');
          setMaxViento('');
        }
      } catch (err) {
        setMessage({
          type: 'error',
          text:
            err instanceof ApiError
              ? err.message
              : 'Error al cargar la configuración del clima.'
        });
      } finally {
        setIsLoading(false);
      }
    };

    cargarConfiguracion();
  }, [id, token]);

  const clearFieldError = (name: string) => {
    setFieldErrors(prev => {
      if (!prev[name]) return prev;
      const next = { ...prev };
      delete next[name];
      return next;
    });
  };

  const handleApplyPreset = (tipo: TipoActividad) => {
    const preset = PRESETS_POR_TIPO[tipo];
    if (!preset) return;

    setHorasAnticipacion(preset.horasAnticipacion);
    setDiasReprogramacion(preset.diasReprogramacion);

    let hInicio = preset.horaInicio;
    let hFinal = preset.horaFinal;
    if (duracionEstimada > 0 && (hFinal - hInicio) < duracionEstimada) {
      hFinal = Math.min(23, hInicio + duracionEstimada);
      if (hFinal - hInicio < duracionEstimada) {
        hInicio = Math.max(0, hFinal - duracionEstimada);
      }
    }
    setHoraInicio(hInicio);
    setHoraFinal(hFinal);

    setMaxProbabilidadLluvia(preset.maxProbabilidadLluvia);
    setMaxViento(preset.maxViento);
    setMinTemperatura(preset.minTemperatura);
    setMaxTemperatura(preset.maxTemperatura);

    setFieldErrors({});
    setMessage({
      type: 'success',
      text: `Se cargaron los valores sugeridos para "${preset.nombre}". Podés revisarlos o ajustarlos antes de guardar.`
    });
  };

  const handleNumberChange = (
    setter: (val: number | '') => void,
    fieldName: string
  ) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    if (raw === '') {
      setter('');
    } else {
      const num = Number(raw);
      setter(isNaN(num) ? '' : num);
    }
    clearFieldError(fieldName);
    if (message) setMessage(null);
  };

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};

    // 1. Horas de anticipación
    if (horasAnticipacion === '') {
      errors.horasAnticipacion = 'Las horas de anticipación son requeridas.';
    } else if (!Number.isInteger(Number(horasAnticipacion))) {
      errors.horasAnticipacion = 'Las horas de anticipación deben ser un número entero.';
    } else if (Number(horasAnticipacion) < 1) {
      errors.horasAnticipacion = 'Debe haber al menos 1 hora de anticipación.';
    } else if (Number(horasAnticipacion) > 72) {
      errors.horasAnticipacion = 'Las horas de anticipación no pueden superar las 72 horas (3 días).';
    }

    // 2. Días de reprogramación (Plan de WeatherAPI admite hasta 3 días)
    if (diasReprogramacion === '') {
      errors.diasReprogramacion = 'El rango máximo de días es requerido.';
    } else if (!Number.isInteger(Number(diasReprogramacion))) {
      errors.diasReprogramacion = 'Los días de reprogramación deben ser un número entero.';
    } else if (Number(diasReprogramacion) < 1) {
      errors.diasReprogramacion = 'El rango de días debe ser de al menos 1 día.';
    } else if (Number(diasReprogramacion) > 3) {
      errors.diasReprogramacion = 'El rango de reprogramación no puede superar los 3 días (límite del pronóstico meteorológico).';
    }

    // 3. Franja horaria de reprogramación y validación con duración estimada
    if (horaInicio === '') {
      errors.horaInicio = 'La hora de inicio es requerida.';
    } else if (!Number.isInteger(Number(horaInicio)) || Number(horaInicio) < 0 || Number(horaInicio) > 23) {
      errors.horaInicio = 'La hora de inicio debe ser un número entero entre 0 y 23 hs.';
    }

    if (horaFinal === '') {
      errors.horaFinal = 'La hora final es requerida.';
    } else if (!Number.isInteger(Number(horaFinal)) || Number(horaFinal) < 0 || Number(horaFinal) > 23) {
      errors.horaFinal = 'La hora final debe ser un número entero entre 0 y 23 hs.';
    } else if (horaInicio !== '') {
      const inicio = Number(horaInicio);
      const fin = Number(horaFinal);
      if (fin <= inicio) {
        errors.horaFinal = 'La hora final permitida debe ser mayor que la hora de inicio.';
      } else if (duracionEstimada > 0) {
        const ventana = fin - inicio;
        if (ventana < duracionEstimada) {
          errors.horaFinal = `La franja horaria permitida (${ventana} hs) no cubre la duración estimada de la actividad (${duracionEstimada} hs).`;
        }
      }
    }

    // 4. Probabilidad de lluvia
    if (maxProbabilidadLluvia === '') {
      errors.maxProbabilidadLluvia = 'La probabilidad máxima de lluvia es requerida.';
    } else if (Number(maxProbabilidadLluvia) < 0 || Number(maxProbabilidadLluvia) > 100) {
      errors.maxProbabilidadLluvia = 'La probabilidad de lluvia debe ser un porcentaje entre 0% y 100%.';
    }

    // 5. Viento máximo (opcional)
    if (maxViento !== '') {
      if (Number(maxViento) < 0) {
        errors.maxViento = 'El viento máximo no puede ser negativo.';
      } else if (Number(maxViento) > 150) {
        errors.maxViento = 'El viento máximo no puede superar los 150 km/h.';
      }
    }

    // 6. Temperaturas (opcionales)
    if (minTemperatura !== '') {
      if (Number(minTemperatura) < -30 || Number(minTemperatura) > 60) {
        errors.minTemperatura = 'La temperatura mínima debe estar entre -30°C y 60°C.';
      }
    }

    if (maxTemperatura !== '') {
      if (Number(maxTemperatura) < -30 || Number(maxTemperatura) > 60) {
        errors.maxTemperatura = 'La temperatura máxima debe estar entre -30°C y 60°C.';
      }
    }

    if (minTemperatura !== '' && maxTemperatura !== '') {
      if (Number(minTemperatura) > Number(maxTemperatura)) {
        errors.maxTemperatura = 'La temperatura máxima no puede ser menor a la temperatura mínima.';
      }
    }

    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setMessage({
        type: 'error',
        text: 'Por favor, revisá los campos del formulario marcados con errores.'
      });
      return false;
    }
    return true;
  };

  const handleOpenConfirm = (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    if (validateForm()) {
      setShowConfirmModal(true);
    }
  };

  const handleConfirmSave = async () => {
    setIsSubmitting(true);
    setMessage(null);

    if (!token) {
      setMessage({
        type: 'error',
        text: 'No estás autenticado.'
      });
      setIsSubmitting(false);
      setShowConfirmModal(false);
      return;
    }

    const payload: ConfigurarCondiciones = {
      horasAnticipacion: Number(horasAnticipacion),
      rangoReprogramacion: {
        dias: Number(diasReprogramacion),
        horaInicio: Number(horaInicio),
        horaFinal: Number(horaFinal)
      },
      reglasClima: {
        maxProbabilidadLluvia: Number(maxProbabilidadLluvia),
        minTemperatura: minTemperatura !== '' ? Number(minTemperatura) : null,
        maxTemperatura: maxTemperatura !== '' ? Number(maxTemperatura) : null,
        maxViento: maxViento !== '' ? Number(maxViento) : null
      }
    };

    try {
      await activitiesApi.configurarClima(id!, payload, token);

      setShowConfirmModal(false);
      setMessage({
        type: 'success',
        text: '¡Configuración de clima guardada exitosamente!'
      });

      setTimeout(() => {
        navigate(`/activities/${id}`);
      }, 1500);

    } catch (err) {
      setShowConfirmModal(false);
      if (err instanceof ApiError) {
        if (err.fieldErrors) {
          setFieldErrors(prev => ({ ...prev, ...err.fieldErrors }));
        }
        setMessage({
          type: 'error',
          text: err.message || 'Error al guardar la configuración del clima.'
        });
      } else {
        setMessage({
          type: 'error',
          text: 'Error inesperado al guardar la configuración del clima.'
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
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

        <Card>
          <CardBody>
            <p className="text-sm text-gray-600">
              Cargando configuración...
            </p>
          </CardBody>
        </Card>
      </div>
    );
  }

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

      {/* Banner de Plantilla Sugerida según Tipo de Actividad */}
      {tipoActividad && PRESETS_POR_TIPO[tipoActividad] && (
        <div className="bg-indigo-50/70 border border-indigo-200/90 rounded-2xl p-4 sm:p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xs">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="text-xl">⚡</span>
              <span className="text-[11px] font-bold uppercase tracking-wider text-indigo-700 bg-indigo-100/90 px-2.5 py-0.5 rounded-full">
                {PRESETS_POR_TIPO[tipoActividad].badge}
              </span>
              <h3 className="text-sm font-bold text-gray-900">
                Plantilla sugerida según tipo de actividad
              </h3>
            </div>
            <p className="text-xs text-gray-600 max-w-xl">
              {PRESETS_POR_TIPO[tipoActividad].descripcion}
            </p>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <Button
              type="button"
              variant="secondary"
              onClick={() => handleApplyPreset(tipoActividad)}
              className="text-xs py-2 px-3.5 bg-white hover:bg-indigo-50 border-indigo-300 text-indigo-700 font-semibold cursor-pointer shadow-xs"
            >
              Cargar sugeridos para {PRESETS_POR_TIPO[tipoActividad].nombre}
            </Button>
          </div>
        </div>
      )}

      <form onSubmit={handleOpenConfirm} noValidate>
        <Card>
          <CardBody className="space-y-8">

            {/* Anticipación del Aviso */}
            <div>
              <h3 className="text-md font-bold text-gray-900 mb-3 border-b pb-1">
                ⏱️ Anticipación del Aviso
              </h3>

              <div>
                <label className="label-text">
                  Horas de anticipación para chequear el clima *
                </label>

                <input
                  type="number"
                  name="horasAnticipacion"
                  min="1"
                  max="72"
                  value={horasAnticipacion}
                  onChange={handleNumberChange(setHorasAnticipacion, 'horasAnticipacion')}
                  className={`input-field max-w-xs ${fieldErrors.horasAnticipacion ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
                {fieldErrors.horasAnticipacion && (
                  <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.horasAnticipacion}</p>
                )}

                <p className="text-xs text-gray-500 mt-1">
                  Con cuántas horas de anticipación se te avisará si el clima va a estar mal (entre 1 y 72 hs).
                </p>
              </div>
            </div>

            {/* Condiciones Climáticas Aceptables */}
            <div>
              <h3 className="text-md font-bold text-gray-900 mb-3 border-b pb-1">
                🌡️ Condiciones Climáticas Aceptables
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">

                {/* Probabilidad Máxima de Lluvia */}
                <div>
                  <label className="label-text">
                    Probabilidad Máxima de Lluvia (%) *
                  </label>

                  <input
                    type="number"
                    name="maxProbabilidadLluvia"
                    min="0"
                    max="100"
                    value={maxProbabilidadLluvia}
                    onChange={handleNumberChange(setMaxProbabilidadLluvia, 'maxProbabilidadLluvia')}
                    className={`input-field ${fieldErrors.maxProbabilidadLluvia ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                  />
                  {fieldErrors.maxProbabilidadLluvia && (
                    <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.maxProbabilidadLluvia}</p>
                  )}
                  <p className="text-xs text-gray-500 mt-1">
                    Porcentaje máximo tolerado antes de sugerir reprogramación (0 a 100%).
                  </p>

                  {/* Referencia contextual de lluvia */}
                  <div className="mt-2 text-xs bg-gray-50 border border-gray-200/80 rounded-lg p-2.5 text-gray-600">
                    <p className="font-semibold text-gray-700 flex items-center gap-1 mb-1">
                      <span>💧</span> Referencia de precipitaciones:
                    </p>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-1 text-[11px]">
                      <span className="text-emerald-700 font-medium">≤ 20%: Riesgo muy bajo</span>
                      <span className="text-amber-700 font-medium">21% - 50%: Riesgo moderado</span>
                      <span className="text-rose-700 font-medium">&gt; 50%: Lluvia probable</span>
                    </div>
                  </div>
                </div>

                {/* Viento Máximo */}
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <label className="label-text mb-0">
                      Viento Máximo (km/h)
                    </label>
                    {maxViento === '' ? (
                      <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-medium">
                        No definido
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setMaxViento('')}
                        className="text-xs text-indigo-600 hover:text-indigo-800 underline cursor-pointer"
                      >
                        Quitar límite
                      </button>
                    )}
                  </div>

                  <input
                    type="number"
                    name="maxViento"
                    min="0"
                    max="150"
                    value={maxViento}
                    placeholder="No definido"
                    onChange={handleNumberChange(setMaxViento, 'maxViento')}
                    className={`input-field ${fieldErrors.maxViento ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                  />
                  {fieldErrors.maxViento && (
                    <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.maxViento}</p>
                  )}
                  <p className="text-xs text-gray-500 mt-1">
                    Opcional. Dejá vacío si no querés límite de velocidad de viento.
                  </p>

                  {/* Referencia contextual de viento */}
                  <div className="mt-2 text-xs bg-gray-50 border border-gray-200/80 rounded-lg p-2.5 text-gray-600">
                    <p className="font-semibold text-gray-700 flex items-center gap-1 mb-1">
                      <span>💨</span> Escala de intensidad de viento:
                    </p>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-1 text-[11px]">
                      <span className="text-emerald-700 font-medium">&lt; 20 km/h: Brisa suave</span>
                      <span className="text-amber-700 font-medium">20 - 45 km/h: Moderado</span>
                      <span className="text-rose-700 font-medium">&gt; 45 km/h: Viento fuerte</span>
                    </div>
                  </div>
                </div>

                {/* Temperatura Mínima Aceptable */}
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <label className="label-text mb-0">
                      Temperatura Mínima Aceptable (°C)
                    </label>
                    {minTemperatura === '' ? (
                      <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-medium">
                        No definido
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setMinTemperatura('')}
                        className="text-xs text-indigo-600 hover:text-indigo-800 underline cursor-pointer"
                      >
                        Quitar límite
                      </button>
                    )}
                  </div>

                  <input
                    type="number"
                    name="minTemperatura"
                    min="-30"
                    max="60"
                    value={minTemperatura}
                    placeholder="No definido"
                    onChange={handleNumberChange(setMinTemperatura, 'minTemperatura')}
                    className={`input-field ${fieldErrors.minTemperatura ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                  />
                  {fieldErrors.minTemperatura && (
                    <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.minTemperatura}</p>
                  )}
                  <p className="text-xs text-gray-500 mt-1">
                    Opcional. Temperatura más baja tolerable para la actividad.
                  </p>
                </div>

                {/* Temperatura Máxima Aceptable */}
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <label className="label-text mb-0">
                      Temperatura Máxima Aceptable (°C)
                    </label>
                    {maxTemperatura === '' ? (
                      <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-medium">
                        No definido
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setMaxTemperatura('')}
                        className="text-xs text-indigo-600 hover:text-indigo-800 underline cursor-pointer"
                      >
                        Quitar límite
                      </button>
                    )}
                  </div>

                  <input
                    type="number"
                    name="maxTemperatura"
                    min="-30"
                    max="60"
                    value={maxTemperatura}
                    placeholder="No definido"
                    onChange={handleNumberChange(setMaxTemperatura, 'maxTemperatura')}
                    className={`input-field ${fieldErrors.maxTemperatura ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                  />
                  {fieldErrors.maxTemperatura && (
                    <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.maxTemperatura}</p>
                  )}
                  <p className="text-xs text-gray-500 mt-1">
                    Opcional. Temperatura más alta tolerable para la actividad.
                  </p>
                </div>

              </div>

              {/* Referencia contextual de temperatura */}
              <div className="mt-3 text-xs bg-gray-50 border border-gray-200/80 rounded-lg p-2.5 text-gray-600">
                <span className="font-semibold text-gray-700">🌡️ Referencia de confort térmico:</span>{' '}
                <span className="text-[11px]">
                  Menos de 10°C suele sentirse como frío riguroso; 15°C a 25°C representa confort templado ideal para actividades recreativas; más de 30°C puede requerir hidratación extra o resguardo.
                </span>
              </div>
            </div>

            {/* Rango para Reprogramar */}
            <div>
              <h3 className="text-md font-bold text-gray-900 mb-3 border-b pb-1">
                📅 Rango para Reprogramar
              </h3>

              <div className="space-y-4">
                <div>
                  <label className="label-text">
                    Rango Máximo de Días *
                  </label>

                  <input
                    type="number"
                    name="diasReprogramacion"
                    min="1"
                    max="3"
                    value={diasReprogramacion}
                    onChange={handleNumberChange(setDiasReprogramacion, 'diasReprogramacion')}
                    className={`input-field max-w-xs ${fieldErrors.diasReprogramacion ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                  />
                  {fieldErrors.diasReprogramacion && (
                    <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.diasReprogramacion}</p>
                  )}
                  <p className="text-xs text-gray-500 mt-1">
                    Máximo 3 días hacia adelante para buscar fechas alternativas (límite del pronóstico meteorológico).
                  </p>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">

                  <div>
                    <label className="label-text">
                      Hora de Inicio Permitida (0 a 23 hs) *
                    </label>

                    <input
                      type="number"
                      name="horaInicio"
                      min="0"
                      max="23"
                      value={horaInicio}
                      onChange={handleNumberChange(setHoraInicio, 'horaInicio')}
                      className={`input-field ${fieldErrors.horaInicio ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                    />
                    {fieldErrors.horaInicio && (
                      <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.horaInicio}</p>
                    )}
                  </div>

                  <div>
                    <label className="label-text">
                      Hora Final Permitida (0 a 23 hs) *
                    </label>

                    <input
                      type="number"
                      name="horaFinal"
                      min="0"
                      max="23"
                      value={horaFinal}
                      onChange={handleNumberChange(setHoraFinal, 'horaFinal')}
                      className={`input-field ${fieldErrors.horaFinal ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                    />
                    {fieldErrors.horaFinal && (
                      <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.horaFinal}</p>
                    )}
                  </div>

                </div>

                {/* Nota de Duración Estimada de la Actividad */}
                {duracionEstimada > 0 && (
                  <div className="p-3 bg-indigo-50/70 border border-indigo-200/80 rounded-xl text-xs text-indigo-900 flex items-start gap-2.5">
                    <span className="text-base">⏱️</span>
                    <div>
                      <p className="font-bold">
                        Duración estimada de esta actividad: {duracionEstimada} {duracionEstimada === 1 ? 'hora' : 'horas'}.
                      </p>
                      <p className="mt-0.5 text-indigo-800">
                        La franja horaria permitida debe abarcar al menos {duracionEstimada} {duracionEstimada === 1 ? 'hora' : 'horas'} completas para poder reprogramar la actividad en caso de mal clima.
                        {horaInicio !== '' && horaFinal !== '' && Number(horaFinal) > Number(horaInicio) && (
                          <span className="font-semibold ml-1">
                            (Ventana actual seleccionada: {Number(horaFinal) - Number(horaInicio)} hs).
                          </span>
                        )}
                      </p>
                    </div>
                  </div>
                )}
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
                Guardar Configuración
              </Button>
            </div>

          </CardBody>
        </Card>
      </form>

      {/* Pop-up Modal de Confirmación */}
      {showConfirmModal && (
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs"
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-weather-modal-title"
        >
          <div className="bg-white rounded-2xl max-w-lg w-full shadow-2xl overflow-hidden border border-gray-100 animate-in fade-in zoom-in duration-200">
            <div className="p-6 border-b border-gray-100 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-2xl">⛅</span>
                <h3 id="confirm-weather-modal-title" className="text-xl font-bold text-gray-900">
                  Confirmar Configuración de Clima
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setShowConfirmModal(false)}
                disabled={isSubmitting}
                className="text-gray-400 hover:text-gray-600 font-bold text-lg p-1 cursor-pointer"
                aria-label="Cerrar modal"
              >
                ✕
              </button>
            </div>

            <div className="p-6 space-y-4 text-sm text-gray-700">
              <p className="font-medium text-gray-800">
                ¿Estás seguro de que deseas guardar los siguientes parámetros de monitoreo climático para esta actividad?
              </p>

              <div className="bg-gray-50 rounded-xl p-4 space-y-2.5 border border-gray-200">
                {actividadTitulo && (
                  <div className="flex justify-between">
                    <span className="text-gray-500 font-medium">Actividad:</span>
                    <span className="font-semibold text-gray-900 text-right truncate max-w-[220px]">{actividadTitulo}</span>
                  </div>
                )}
                {tipoActividad && (
                  <div className="flex justify-between">
                    <span className="text-gray-500 font-medium">Tipo:</span>
                    <span className="font-semibold text-gray-900">{PRESETS_POR_TIPO[tipoActividad]?.nombre ?? tipoActividad}</span>
                  </div>
                )}
                {duracionEstimada > 0 && (
                  <div className="flex justify-between">
                    <span className="text-gray-500 font-medium">Duración estimada:</span>
                    <span className="font-semibold text-gray-900">{duracionEstimada} hs</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Anticipación del aviso:</span>
                  <span className="font-semibold text-gray-900">{horasAnticipacion} horas</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Rango de reprogramación:</span>
                  <span className="font-semibold text-gray-900">Hasta {diasReprogramacion} días</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Franja horaria permitida:</span>
                  <span className="font-medium text-gray-900">De {horaInicio}:00 hs a {horaFinal}:00 hs</span>
                </div>
                <div className="border-t border-gray-200 my-2 pt-2">
                  <span className="text-xs uppercase font-bold text-gray-400 tracking-wider">Reglas meteorológicas</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Probabilidad máx. de lluvia:</span>
                  <span className="font-semibold text-gray-900">{maxProbabilidadLluvia}%</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Viento máximo:</span>
                  <span className={`font-semibold ${maxViento !== '' ? 'text-gray-900' : 'text-gray-500 italic'}`}>
                    {maxViento !== '' ? `${maxViento} km/h` : 'No definido'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Temperatura mínima aceptable:</span>
                  <span className={`font-semibold ${minTemperatura !== '' ? 'text-gray-900' : 'text-gray-500 italic'}`}>
                    {minTemperatura !== '' ? `${minTemperatura} °C` : 'No definido'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Temperatura máxima aceptable:</span>
                  <span className={`font-semibold ${maxTemperatura !== '' ? 'text-gray-900' : 'text-gray-500 italic'}`}>
                    {maxTemperatura !== '' ? `${maxTemperatura} °C` : 'No definido'}
                  </span>
                </div>
              </div>
            </div>

            <div className="p-4 bg-gray-50 border-t border-gray-100 flex justify-end gap-3">
              <Button
                variant="secondary"
                type="button"
                onClick={() => setShowConfirmModal(false)}
                disabled={isSubmitting}
              >
                Cancelar
              </Button>
              <Button
                variant="primary"
                type="button"
                onClick={handleConfirmSave}
                disabled={isSubmitting}
              >
                {isSubmitting ? 'Guardando...' : 'Confirmar y Guardar'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
