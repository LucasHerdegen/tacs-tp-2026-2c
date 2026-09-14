import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Card, CardBody } from '../../../components/ui/Card';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import type { ActividadPostDto } from '../../../types/activity.types';
import { TipoActividad } from '../../../types/activity.types';
import { apiRequest, ApiError } from '../../../lib/api';
import { useAuth } from '../../auth/authContext';

export const CreateActivity: React.FC = () => {
  useDocumentTitle('Crear Nueva Actividad');
  const navigate = useNavigate();

  const [formData, setFormData] = useState<ActividadPostDto>({
    titulo: '',
    descripcion: '',
    tipoActividad: TipoActividad.AIRE_LIBRE,
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

  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
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
    await apiRequest('/api/actividades', {
      method: 'POST',
      token,
      body: JSON.stringify(formData),
    });

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
          La actividad nacerá en estado <span className="font-semibold text-indigo-600">PROPUESTA</span> sin monitoreo de clima activado. Podrás configurarlo más adelante.
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
                placeholder="Detalles sobre la junta, requerimientos, etc."
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
                  <option value={TipoActividad.AIRE_LIBRE}>Aire Libre</option>
                  <option value={TipoActividad.TECHADA}>Techada</option>
                  <option value={TipoActividad.MIXTA}>Mixta</option>
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

            {/* Ubicación (Barrio) */}
            <div>
              <label className="label-text">Barrio / Ubicación *</label>
              <input
                type="text"
                name="barrio"
                required
                value={formData.ubicacion.barrio}
                onChange={handleUbicacionChange}
                placeholder="Ej: Palermo, CABA / Parque Sarmiento"
                className="input-field"
              />
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