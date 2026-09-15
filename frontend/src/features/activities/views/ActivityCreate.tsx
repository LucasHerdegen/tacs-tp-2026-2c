import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, useMapEvents, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import { Card, CardBody } from '../../../components/ui/Card';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import type { ActividadPost } from '../types';
// import type { TipoActividad } from '../types'; // Hace que falle build mientras no se use.
import { ApiError } from '../../../lib/api';
import { useAuth } from '../../auth/authContext';
import { activitiesApi } from '../activitiesApi';

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

const LocationMarker: React.FC<{
  position: { lat: number; lng: number };
  setPosition: (lat: number, lng: number) => void;
}> = ({ position, setPosition }) => {
  const map = useMap();

  useMapEvents({
    click(e) {
      setPosition(e.latlng.lat, e.latlng.lng);
      map.flyTo(e.latlng, map.getZoom());
    },
  });

  return <Marker position={[position.lat, position.lng]} />;
};

const MapController: React.FC<{ center: [number, number]; zoom?: number }> = ({ center, zoom = 15 }) => {
  const map = useMap();
  React.useEffect(() => {
    map.flyTo(center, zoom, { duration: 1.5 });
  }, [center, zoom, map]);

  return null;
};

export const CreateActivity: React.FC = () => {
  useDocumentTitle('Crear Nueva Actividad');
  const navigate = useNavigate();

  const [formData, setFormData] = useState<ActividadPost>({
    titulo: '',
    descripcion: '',
    tipoActividad: 'AIRE_LIBRE',
    ubicacion: {
      barrio: '',
      latitud: -34.6037, // Valor default CABA
      longitud: -58.3816
    },
    fecha: '',
    duracionEstimada: 2,
    cantidadMinima: 2,
    cantidadMaxima: 10
  });

  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isSearching, setIsSearching] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;

    setFormData(prev => ({
      ...prev,
      [name]:
        name === 'duracionEstimada' ||
        name === 'cantidadMinima' ||
        name === 'cantidadMaxima'
          ? Number(value)
          : value
    }));
  };

  const handleUbicacionChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      ubicacion: {
        ...prev.ubicacion,
        [name]: name === 'barrio' ? value : Number(value)
      }
    }));
  };

  // Actualiza las coordenadas en el estado
  const handleMapClick = (lat: number, lng: number) => {
    setFormData(prev => ({
      ...prev,
      ubicacion: {
        ...prev.ubicacion,
        latitud: Number(lat.toFixed(6)),
        longitud: Number(lng.toFixed(6))
      }
    }));
  };

  const handleSearchAddress = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!searchQuery.trim()) return;

    setIsSearching(true);
    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(searchQuery)}`
      );
      const data = await response.json();

      if (data && data.length > 0) {
        const { lat, lon } = data[0];
        handleMapClick(parseFloat(lat), parseFloat(lon));
      } else {
        alert('No se encontraron resultados para esa dirección.');
      }
    } catch (err) {
      console.error('Error al buscar dirección:', err);
    } finally {
      setIsSearching(false);
    }
  };

  const { token } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validación según reglas del Backend
    if (formData.cantidadMinima > formData.cantidadMaxima) {
      setError('La cantidad mínima de participantes no puede ser mayor a la cantidad máxima');
      return;
    }

    if (formData.cantidadMinima < 2) {
      setError('La actividad debe contar con por lo menos 2 personas');
      return;
    }

    if (!token) {
      setError('No estás autenticado.');
      return;
    }

    setIsSubmitting(true);

    try {
      await activitiesApi.crear(formData, token);

      navigate('/activities');
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'Ocurrió un error al intentar crear la actividad.'
      );
    } finally {
      setIsSubmitting(false);
    }
  };

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
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-xl text-sm font-medium">
          ⚠️ {error}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <Card>
          <CardBody className="space-y-6">
            
            {/* Título */}
            <div>
              <label className="label-text">Título *</label>
              <input
                type="text"
                name="titulo"
                required
                value={formData.titulo}
                onChange={handleChange}
                placeholder="Ej: Partido de Fútbol 5 / Asado de fin de año"
                className="input-field"
              />
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
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="label-text">Tipo de Actividad *</label>
                <select
                  name="tipoActividad"
                  value={formData.tipoActividad}
                  onChange={handleChange}
                  className="input-field bg-white"
                >
                  <option value={'AIRE_LIBRE'}>Aire Libre</option>
                  <option value={'TECHADA'}>Techada</option>
                  <option value={'MIXTA'}>Mixta</option>
                </select>
              </div>

              <div>
                <label className="label-text">Fecha y Hora *</label>
                <input
                  type="datetime-local"
                  name="fecha"
                  required
                  value={formData.fecha}
                  onChange={handleChange}
                  className="input-field"
                />
              </div>
            </div>

            {/* Ubicación (Barrio, Buscador y Mapa) */}
            <div className="space-y-4">
              <div>
                <label className="label-text"> Lugar *</label>
                <input
                  type="text"
                  name="barrio"
                  required
                  value={formData.ubicacion.barrio}
                  onChange={handleUbicacionChange}
                  placeholder="Ej: Ramos Mejia / Parque Sarmiento"
                  className="input-field"
                />
              </div>

              {/* Buscador de dirección */}
              <div>
                <label className="label-text mb-1 block">
                  Seleccionar ubicación en el mapa *
                </label>
                <div className="flex gap-2 mb-2">
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

                <p className="text-xs text-gray-500 mb-2">
                  Buscá un lugar arriba o hacé click directamente en el mapa para ajustar la marca.
                </p>

                {/* Mapa con Autocentrado */}
                <div className="h-64 w-full rounded-lg overflow-hidden border border-gray-300">
                  <MapContainer
                    center={[formData.ubicacion.latitud, formData.ubicacion.longitud]}
                    zoom={13}
                    style={{ height: '100%', width: '100%' }}
                  >
                    <TileLayer
                      attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                      url="https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
                    />
                    
                    {/* Controla la re-centrada animada del mapa */}
                    <MapController
                      center={[formData.ubicacion.latitud, formData.ubicacion.longitud]}
                      zoom={15}
                    />

                    <LocationMarker
                      position={{
                        lat: formData.ubicacion.latitud,
                        lng: formData.ubicacion.longitud,
                      }}
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
                  required
                  value={formData.duracionEstimada}
                  onChange={handleChange}
                  className="input-field"
                />
              </div>

              <div>
                <label className="label-text">Capacidad Mínima *</label>
                <input
                  type="number"
                  name="cantidadMinima"
                  min="2"
                  required
                  value={formData.cantidadMinima}
                  onChange={handleChange}
                  className="input-field"
                />
              </div>

              <div>
                <label className="label-text">Capacidad Máxima *</label>
                <input
                  type="number"
                  name="cantidadMaxima"
                  min="2"
                  required
                  value={formData.cantidadMaxima}
                  onChange={handleChange}
                  className="input-field"
                />
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
                {isSubmitting ? 'Creando...' : 'Crear Actividad'}
              </Button>
            </div>

          </CardBody>
        </Card>
      </form>
    </div>
  );
};
