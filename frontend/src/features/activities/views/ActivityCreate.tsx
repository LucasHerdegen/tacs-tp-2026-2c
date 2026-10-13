import React, { useState, useMemo, useRef, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, useMapEvents, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import { Card, CardBody } from '../../../components/ui/Card';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import type { ActividadPost, TipoActividad } from '../types';
import { ApiError } from '../../../lib/api';
import { useAuth } from '../../auth/authContext';
import { activitiesApi } from '../activitiesApi';
import { formatFechaHora } from '../../../utils/dateTime';

// Fix para los íconos por defecto de Leaflet en React
type LeafletIconDefaultPrototype = L.Icon.Default & {
  _getIconUrl?: (name: string) => string;
};

delete (L.Icon.Default.prototype as LeafletIconDefaultPrototype)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const TIPO_ACTIVIDAD_LABEL: Record<TipoActividad, string> = {
  AIRE_LIBRE: 'Aire libre',
  TECHADA: 'Techada',
  MIXTA: 'Mixta',
};

const LocationMarker: React.FC<{
  position: { lat: number; lng: number } | null;
  setPosition: (lat: number, lng: number) => void;
}> = ({ position, setPosition }) => {
  useMapEvents({
    click(e) {
      setPosition(e.latlng.lat, e.latlng.lng);
    },
  });

  if (!position) {
    return null;
  }

  return <Marker position={[position.lat, position.lng]} />;
};

const MapController: React.FC<{ center: [number, number]; zoom?: number }> = ({ center, zoom = 15 }) => {
  const map = useMap();
  const prevCenterRef = useRef<[number, number] | null>(null);

  useEffect(() => {
    const [lat, lng] = center;
    if (!prevCenterRef.current) {
      prevCenterRef.current = [lat, lng];
      return;
    }

    const [prevLat, prevLng] = prevCenterRef.current;
    if (prevLat !== lat || prevLng !== lng) {
      prevCenterRef.current = [lat, lng];
      map.flyTo([lat, lng], zoom, { duration: 1.2 });
    }
  }, [center, zoom, map]);

  return null;
};

interface FormState {
  titulo: string;
  descripcion: string;
  tipoActividad: TipoActividad;
  ubicacion: {
    ciudad: string;
    latitud: number | null;
    longitud: number | null;
  };
  fecha: string;
  duracionEstimada: number | '';
  cantidadMinima: number | '';
  cantidadMaxima: number | '';
}

interface AddressSuggestion {
  lat: string;
  lon: string;
  display_name: string;
}

export const CreateActivity: React.FC = () => {
  useDocumentTitle('Crear Nueva Actividad');
  const navigate = useNavigate();

  const [formData, setFormData] = useState<FormState>({
    titulo: '',
    descripcion: '',
    tipoActividad: 'AIRE_LIBRE',
    ubicacion: {
      ciudad: '',
      latitud: null,
      longitud: null
    },
    fecha: '',
    duracionEstimada: 2,
    cantidadMinima: 2,
    cantidadMaxima: 10
  });

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [searchResults, setSearchResults] = useState<AddressSuggestion[]>([]);
  const [isSearching, setIsSearching] = useState<boolean>(false);
  const [isLocating, setIsLocating] = useState<boolean>(false);
  const [locatingError, setLocatingError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [showConfirmModal, setShowConfirmModal] = useState<boolean>(false);

  // Calcula la fecha y hora mínima en formato YYYY-MM-DDTHH:mm local
  const minDateTime = useMemo(() => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }, []);

  const clearFieldError = (name: string) => {
    setFieldErrors(prev => {
      if (!prev[name]) return prev;
      const next = { ...prev };
      delete next[name];
      return next;
    });
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;

    setFormData(prev => ({
      ...prev,
      [name]:
        name === 'duracionEstimada' ||
        name === 'cantidadMinima' ||
        name === 'cantidadMaxima'
          ? (value === '' ? '' : Number(value))
          : value
    }));

    clearFieldError(name);
    if (error) setError(null);
  };

  const handleUbicacionChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      ubicacion: {
        ...prev.ubicacion,
        [name]: name === 'ciudad'
          ? value
          : value === ''
            ? null
            : Number(value)
      }
    }));

    if (name === 'ciudad') {
      clearFieldError('ciudad');
    }
    if (error) setError(null);
  };

  // Actualiza las coordenadas en el estado y realiza reverse geocoding si el lugar está vacío
  const handleMapClick = async (lat: number, lng: number) => {
    const formattedLat = Number(lat.toFixed(6));
    const formattedLng = Number(lng.toFixed(6));

    setFormData(prev => ({
      ...prev,
      ubicacion: {
        ...prev.ubicacion,
        latitud: formattedLat,
        longitud: formattedLng
      }
    }));

    // Reverse geocoding con OpenStreetMap Nominatim
    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/reverse?format=json&lat=${formattedLat}&lon=${formattedLng}`
      );
      const data = await response.json();
      if (data && data.display_name) {
        const parts = data.display_name.split(',').map((p: string) => p.trim());
        const suggested = parts.slice(0, 2).join(', ');
        if (suggested) {
          setFormData(prev => ({
            ...prev,
            ubicacion: {
              ...prev.ubicacion,
              ciudad: suggested
            }
          }));
          clearFieldError('ciudad');
        }
      }
    } catch (err) {
      console.warn('Reverse geocoding error:', err);
    }
  };

  const selectAddress = (item: AddressSuggestion) => {
    const lat = parseFloat(item.lat);
    const lon = parseFloat(item.lon);
    const cleanName = item.display_name.split(',').slice(0, 2).join(', ').trim();
    const formattedLat = Number(lat.toFixed(6));
    const formattedLng = Number(lon.toFixed(6));

    setFormData(prev => ({
      ...prev,
      ubicacion: {
        ...prev.ubicacion,
        latitud: formattedLat,
        longitud: formattedLng,
        ciudad: cleanName || searchQuery.trim()
      }
    }));
    clearFieldError('ciudad');
    setSearchResults([]);
  };

  const handleSearchAddress = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!searchQuery.trim()) return;

    setIsSearching(true);
    setSearchResults([]);
    setLocatingError(null);

    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&limit=4&q=${encodeURIComponent(searchQuery)}`
      );
      const data = await response.json();

      if (Array.isArray(data) && data.length > 0) {
        if (data.length === 1) {
          selectAddress(data[0]);
        } else {
          setSearchResults(data);
        }
      } else {
        alert('No se encontraron resultados para esa dirección.');
      }
    } catch (err) {
      console.error('Error al buscar dirección:', err);
    } finally {
      setIsSearching(false);
    }
  };

  const handleUseCurrentLocation = () => {
    if (!navigator.geolocation) {
      setLocatingError('Tu navegador no soporta geolocalización.');
      return;
    }

    setIsLocating(true);
    setLocatingError(null);

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        handleMapClick(lat, lng);
        setIsLocating(false);
      },
      (err) => {
        console.warn('Geolocation error:', err);
        setLocatingError('No se pudo acceder a tu ubicación (permiso denegado o no disponible).');
        setIsLocating(false);
      },
      { timeout: 8000 }
    );
  };

  const handleClearMarker = () => {
    setFormData(prev => ({
      ...prev,
      ubicacion: {
        ...prev.ubicacion,
        latitud: null,
        longitud: null,
        ciudad: ''
      }
    }));
    setSearchQuery('');
    setSearchResults([]);
  };

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};

    if (!formData.titulo.trim()) {
      errors.titulo = 'El título es obligatorio.';
    }

    if (!formData.tipoActividad) {
      errors.tipoActividad = 'El tipo de actividad es obligatorio.';
    }

    if (!formData.fecha) {
      errors.fecha = 'La fecha y hora son obligatorias.';
    } else {
      const fechaSeleccionada = new Date(formData.fecha);
      const ahora = new Date();
      if (isNaN(fechaSeleccionada.getTime())) {
        errors.fecha = 'La fecha ingresada no es válida.';
      } else if (fechaSeleccionada <= ahora) {
        errors.fecha = 'La fecha y hora deben ser posteriores al momento actual.';
      }
    }

    if (!formData.ubicacion.ciudad.trim()) {
      errors.ciudad = 'El lugar es obligatorio.';
    }

    if (formData.duracionEstimada === '' || Number(formData.duracionEstimada) < 1) {
      errors.duracionEstimada = 'La duración estimada debe ser de al menos 1 hora.';
    }

    if (formData.cantidadMinima === '' || Number(formData.cantidadMinima) < 2) {
      errors.cantidadMinima = 'La capacidad mínima debe ser de al menos 2 personas.';
    }

    if (formData.cantidadMaxima === '' || Number(formData.cantidadMaxima) < 2) {
      errors.cantidadMaxima = 'La capacidad máxima debe ser de al menos 2 personas.';
    } else if (
      formData.cantidadMinima !== '' &&
      Number(formData.cantidadMinima) > Number(formData.cantidadMaxima)
    ) {
      errors.cantidadMaxima = 'La capacidad máxima no puede ser menor a la capacidad mínima.';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const { token } = useAuth();

  const handlePreSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!validateForm()) {
      return;
    }

    setShowConfirmModal(true);
  };

  const handleConfirmCreate = async () => {
    if (!token) {
      setError('No estás autenticado.');
      setShowConfirmModal(false);
      return;
    }

    setIsSubmitting(true);
    setError(null);

    const payload: ActividadPost = {
      titulo: formData.titulo.trim(),
      descripcion: formData.descripcion.trim(),
      tipoActividad: formData.tipoActividad,
      ubicacion: {
        ciudad: formData.ubicacion.ciudad.trim(),
        latitud: formData.ubicacion.latitud,
        longitud: formData.ubicacion.longitud
      },
      fecha: formData.fecha,
      duracionEstimada: Number(formData.duracionEstimada),
      cantidadMinima: Number(formData.cantidadMinima),
      cantidadMaxima: Number(formData.cantidadMaxima)
    };

    try {
      const nuevaActividad = await activitiesApi.crear(payload, token);
      setShowConfirmModal(false);
      navigate('/my-activities', {
        state: {
          successMessage: '¡La actividad se ha creado exitosamente!',
          newActivityId: nuevaActividad?.id
        }
      });
    } catch (err) {
      setShowConfirmModal(false);
      if (err instanceof ApiError) {
        if (err.fieldErrors) {
          setFieldErrors(prev => ({ ...prev, ...err.fieldErrors }));
        }
        setError(err.message);
      } else {
        setError('Ocurrió un error inesperado al intentar crear la actividad.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const lat = formData.ubicacion.latitud ?? -34.6037;
  const lng = formData.ubicacion.longitud ?? -58.3816;
  const mapCenter = useMemo<[number, number]>(() => [lat, lng], [lat, lng]);

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <Link to="/activities" className="text-sm font-semibold text-indigo-600 hover:text-indigo-800 flex items-center gap-1 mb-2">
          ← Volver a actividades
        </Link>
        <h1 className="text-3xl font-bold text-gray-900">Crear Nueva Actividad</h1>
        <p className="text-gray-600 text-sm mt-1">
          💡 La actividad se creara sin monitoreo de clima activado. Podrás configurarlo más adelante.
        </p>
      </div>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-xl text-sm font-medium flex items-center gap-2">
          <span>⚠️</span>
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handlePreSubmit} noValidate>
        <Card>
          <CardBody className="space-y-6">

            {/* Título */}
            <div>
              <label className="label-text">Título *</label>
              <input
                type="text"
                name="titulo"
                value={formData.titulo}
                onChange={handleChange}
                placeholder="Ej: Partido de Fútbol 5 / Asado de fin de año"
                className={`input-field ${fieldErrors.titulo ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
              />
              {fieldErrors.titulo && (
                <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.titulo}</p>
              )}
            </div>

            {/* Descripción */}
            <div>
              <label className="label-text">Descripción</label>
              <textarea
                name="descripcion"
                rows={3}
                value={formData.descripcion}
                onChange={handleChange}
                placeholder="Detalles sobre la juntada, requisitos, etc."
                className="input-field"
              />
            </div>

            {/* Tipo de Actividad y Fecha */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label className="label-text">Tipo de Actividad *</label>
                <select
                  name="tipoActividad"
                  value={formData.tipoActividad}
                  onChange={handleChange}
                  className={`input-field bg-white ${fieldErrors.tipoActividad ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                >
                  <option value={'AIRE_LIBRE'}>Aire Libre</option>
                  <option value={'TECHADA'}>Techada</option>
                  <option value={'MIXTA'}>Mixta</option>
                </select>
                {fieldErrors.tipoActividad && (
                  <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.tipoActividad}</p>
                )}
              </div>

              <div className="sm:col-span-2">
                <label className="label-text">Fecha y Hora *</label>
                <input
                  type="datetime-local"
                  name="fecha"
                  min={minDateTime}
                  value={formData.fecha}
                  onChange={handleChange}
                  className={`input-field ${fieldErrors.fecha ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
                {fieldErrors.fecha && (
                  <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.fecha}</p>
                )}
              </div>
            </div>

            {/* Ubicación (Ciudad, Buscador y Mapa) */}
            <div className="space-y-4">
              <div>
                <label className="label-text">Lugar *</label>
                <input
                  type="text"
                  name="ciudad"
                  value={formData.ubicacion.ciudad}
                  onChange={handleUbicacionChange}
                  placeholder="Ej: Ramos Mejia / Parque Sarmiento"
                  className={`input-field ${fieldErrors.ciudad ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
                {fieldErrors.ciudad && (
                  <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.ciudad}</p>
                )}
              </div>

              {/* Buscador de dirección y Geolocation */}
              <div>
                <div className="flex justify-between items-center mb-1">
                  <label className="label-text block">
                    Ubicación en el mapa (opcional)
                  </label>
                  {formData.ubicacion.latitud !== null && formData.ubicacion.longitud !== null && (
                    <button
                      type="button"
                      onClick={handleClearMarker}
                      className="text-xs text-red-600 hover:text-red-800 font-medium flex items-center gap-1 cursor-pointer transition"
                    >
                      ✕ Quitar marcador
                    </button>
                  )}
                </div>

                <div className="flex flex-col sm:flex-row gap-2 mb-2 relative">
                  <div className="flex-grow flex gap-2">
                    <input
                      type="text"
                      placeholder="Ej: Av. Corrientes 1234, CABA"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleSearchAddress())}
                      className="input-field text-sm"
                    />
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={handleSearchAddress}
                      disabled={isSearching}
                    >
                      {isSearching ? 'Buscando...' : 'Buscar'}
                    </Button>
                  </div>

                  <Button
                    type="button"
                    variant="secondary"
                    onClick={handleUseCurrentLocation}
                    disabled={isLocating}
                    className="whitespace-nowrap flex items-center justify-center gap-1.5 text-xs sm:text-sm"
                    title="Usar ubicación GPS actual"
                  >
                    <span>📍</span>
                    <span>{isLocating ? 'Ubicando...' : 'Mi ubicación'}</span>
                  </Button>
                </div>

                {locatingError && (
                  <p className="text-xs text-amber-600 mb-2">⚠️ {locatingError}</p>
                )}

                {/* Lista de sugerencias si la búsqueda trajo varios resultados */}
                {searchResults.length > 0 && (
                  <div className="bg-white border border-gray-200 rounded-xl shadow-lg mb-3 p-1.5 space-y-1 text-sm z-20">
                    <p className="px-3 py-1 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Seleccioná la ubicación correcta:
                    </p>
                    {searchResults.map((result, idx) => (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => selectAddress(result)}
                        className="w-full text-left px-3 py-2 rounded-lg hover:bg-indigo-50 hover:text-indigo-900 transition flex items-start gap-2 cursor-pointer"
                      >
                        <span className="text-indigo-600 mt-0.5">📍</span>
                        <span className="line-clamp-2 text-xs text-gray-800">{result.display_name}</span>
                      </button>
                    ))}
                  </div>
                )}

                <p className="text-xs text-gray-500 mb-2">
                  💡 Buscá una dirección, usá tu ubicación o hacé click en el mapa. El nombre del lugar se sugerirá automáticamente.
                </p>

                {/* Mapa con Autocentrado */}
                <div className="h-64 w-full rounded-lg overflow-hidden border border-gray-300 relative z-0 isolate">
                  <MapContainer
                    center={mapCenter}
                    zoom={13}
                    style={{ height: '100%', width: '100%' }}
                  >
                    <TileLayer
                      attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                      url="https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
                    />

                    {/* Controla la re-centrada animada del mapa */}
                    <MapController
                      center={mapCenter}
                      zoom={15}
                    />

                    <LocationMarker
                      position={
                        formData.ubicacion.latitud !== null &&
                        formData.ubicacion.longitud !== null
                          ? {
                              lat: formData.ubicacion.latitud,
                              lng: formData.ubicacion.longitud,
                            }
                          : null
                      }
                      setPosition={handleMapClick}
                    />
                  </MapContainer>
                </div>
              </div>
            </div>

            {/* Duración y Participantes */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label className="label-text">Duración estimada (hs) *</label>
                <input
                  type="number"
                  name="duracionEstimada"
                  min="1"
                  value={formData.duracionEstimada}
                  onChange={handleChange}
                  className={`input-field ${fieldErrors.duracionEstimada ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
                {fieldErrors.duracionEstimada && (
                  <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.duracionEstimada}</p>
                )}
              </div>

              <div>
                <label className="label-text">Capacidad Mínima *</label>
                <input
                  type="number"
                  name="cantidadMinima"
                  min="2"
                  value={formData.cantidadMinima}
                  onChange={handleChange}
                  className={`input-field ${fieldErrors.cantidadMinima ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
                {fieldErrors.cantidadMinima && (
                  <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.cantidadMinima}</p>
                )}
              </div>

              <div>
                <label className="label-text">Capacidad Máxima *</label>
                <input
                  type="number"
                  name="cantidadMaxima"
                  min="2"
                  value={formData.cantidadMaxima}
                  onChange={handleChange}
                  className={`input-field ${fieldErrors.cantidadMaxima ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
                {fieldErrors.cantidadMaxima && (
                  <p className="mt-1 text-xs text-red-600 font-medium">⚠️ {fieldErrors.cantidadMaxima}</p>
                )}
              </div>
            </div>

            {/* Botones de Acción */}
            <div className="pt-4 flex justify-end gap-3 border-t border-gray-100">
              <Link to="/activities">
                <Button variant="secondary" type="button" disabled={isSubmitting}>
                  Cancelar
                </Button>
              </Link>
              <Button variant="primary" type="submit" disabled={isSubmitting}>
                Crear Actividad
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
          aria-labelledby="confirm-modal-title"
        >
          <div className="bg-white rounded-2xl max-w-lg w-full shadow-2xl overflow-hidden border border-gray-100 animate-in fade-in zoom-in duration-200">
            <div className="p-6 border-b border-gray-100 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-2xl">📋</span>
                <h3 id="confirm-modal-title" className="text-xl font-bold text-gray-900">
                  Confirmar Nueva Actividad
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
                ¿Estás seguro de que deseas crear esta actividad con los siguientes datos?
              </p>

              <div className="bg-gray-50 rounded-xl p-4 space-y-2 border border-gray-200">
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Título:</span>
                  <span className="font-semibold text-gray-900 text-right">{formData.titulo}</span>
                </div>
                {formData.descripcion && (
                  <div className="flex justify-between gap-4">
                    <span className="text-gray-500 font-medium">Descripción:</span>
                    <span className="text-gray-800 text-right line-clamp-2">{formData.descripcion}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Tipo:</span>
                  <span className="font-medium text-gray-900">{TIPO_ACTIVIDAD_LABEL[formData.tipoActividad]}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Fecha y hora:</span>
                  <span className="font-medium text-gray-900">
                    {formData.fecha ? `${formatFechaHora(formData.fecha)} hs` : '-'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Lugar:</span>
                  <span className="font-semibold text-gray-900">{formData.ubicacion.ciudad}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Duración:</span>
                  <span className="font-medium text-gray-900">{formData.duracionEstimada} hs</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Capacidad:</span>
                  <span className="font-medium text-gray-900">
                    Mín. {formData.cantidadMinima} / Máx. {formData.cantidadMaxima} participantes
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500 font-medium">Ubicación geográfica:</span>
                  <span className="font-medium text-gray-600">
                    {formData.ubicacion.latitud !== null && formData.ubicacion.longitud !== null
                      ? `(${formData.ubicacion.latitud}, ${formData.ubicacion.longitud})`
                      : 'No especificada en mapa'}
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
                onClick={handleConfirmCreate}
                disabled={isSubmitting}
              >
                {isSubmitting ? 'Creando...' : 'Confirmar y Crear'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
