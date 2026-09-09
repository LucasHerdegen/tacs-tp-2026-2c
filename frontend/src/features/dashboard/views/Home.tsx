import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '../../../components/ui/Button';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';

export const Home: React.FC = () => {
  useDocumentTitle('Inicio');
  const navigate = useNavigate();
  return (
    <div className="space-y-8">
      {/* Hero Section */}
      <section className="text-center py-12 px-4 sm:px-6 lg:px-8 bg-white rounded-2xl shadow-sm border border-gray-100">
        <h1 className="text-4xl font-extrabold text-gray-900 sm:text-5xl">
          Que el clima no arruine tus planes
        </h1>
        <p className="mt-4 text-xl text-gray-500 max-w-2xl mx-auto">
          Organizá asados, partidos o salidas. Nosotros monitoreamos el pronóstico por vos y te avisamos si hay que reprogramar.
        </p>
        <div className="mt-8 flex justify-center gap-4">
          <Button variant="primary" className="text-lg px-8 py-3">
            Crear Actividad
          </Button>
          <Button variant="secondary" className="text-lg px-8 py-3" onClick={() => navigate('/activities')}>
            Buscar Actividades
          </Button>
        </div>
      </section>

      {/* Quick Overview (Mock Data) */}
      <section>
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Actividades Destacadas</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          
          {/* Card 1 */}
          <Card>
            <CardBody>
              <div className="flex justify-between items-start mb-4">
                <h3 className="text-lg font-semibold text-gray-900">Asado de Fin de Año</h3>
                <Badge variant="success">Clima Ideal</Badge>
              </div>
              <p className="text-sm text-gray-600 mb-4">
                📍 Parque Sarmiento <br/>
                📅 Sab 14 de Nov, 12:30 hs
              </p>
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-gray-500">12 / 20 participantes</span>
                <Button variant="secondary" className="text-xs px-3 py-1" onClick={() => navigate('/activities/1')}>Ver detalles</Button>
              </div>
            </CardBody>
          </Card>

          {/* Card 2 */}
          <Card>
            <CardBody>
              <div className="flex justify-between items-start mb-4">
                <h3 className="text-lg font-semibold text-gray-900">Partido Fútbol 5</h3>
                <Badge variant="warning">Alerta Lluvia</Badge>
              </div>
              <p className="text-sm text-gray-600 mb-4">
                📍 Canchas El Templo <br/>
                📅 Mar 17 de Nov, 20:00 hs
              </p>
              <div className="flex justify-between items-center">
                <span className="text-xs font-medium text-gray-500">9 / 10 participantes</span>
                <Button variant="secondary" className="text-xs px-3 py-1" onClick={() => navigate('/activities/2')}>Ver detalles</Button>
              </div>
            </CardBody>
          </Card>

          {/* Card 3 */}
          <Card className="border-indigo-100 bg-indigo-50/30">
            <CardBody className="flex flex-col items-center justify-center h-full text-center py-8">
              <span className="text-3xl mb-2">📥</span>
              <h3 className="text-md font-semibold text-gray-900 mb-1">¿Tenés un plan?</h3>
              <p className="text-sm text-gray-500 mb-4">Invitá a tus amigos y dejá que el sistema controle el clima.</p>
              <button className="text-indigo-600 font-semibold hover:text-indigo-800 text-sm cursor-pointer">
                + Nueva actividad
              </button>
            </CardBody>
          </Card>

        </div>
      </section>
    </div>
  );
};
