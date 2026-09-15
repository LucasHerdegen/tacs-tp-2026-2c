import React, { useEffect, useState } from 'react';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge, type BadgeVariant } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useDocumentTitle } from '../../../hooks/useDocumentTitle';
import { ApiError } from '../../../lib/api';
import { useAuth } from '../../auth/authContext';
import { ESTADO_BADGE } from '../../activities/activityLabels';
import type { TipoEstadoActividad } from '../../activities/types';
import { adminApi } from '../adminApi';
import type { EstadisticasDto } from '../types';

const BARRA_COLOR: Record<BadgeVariant, string> = {
  success: 'bg-green-500',
  warning: 'bg-yellow-500',
  error: 'bg-red-500',
  info: 'bg-indigo-500',
  neutral: 'bg-gray-400',
};

function porcentaje(cantidad: number, total: number): number {
  return total === 0 ? 0 : Math.round((cantidad / total) * 100);
}

export const AdminStats: React.FC = () => {
  useDocumentTitle('Estadísticas');
  const { token } = useAuth();

  const [estadisticas, setEstadisticas] = useState<EstadisticasDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (!token) return;
    let ignore = false;

    adminApi
      .estadisticas(token)
      .then((datos) => {
        if (ignore) return;
        setEstadisticas(datos);
        setError('');
      })
      .catch((requestError: unknown) => {
        if (ignore) return;
        setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cargar las estadísticas.');
      })
      .finally(() => {
        if (ignore) return;
        setLoading(false);
        setRefreshing(false);
      });

    return () => {
      ignore = true;
    };
  }, [token, refreshKey]);

  function handleActualizar() {
    setRefreshing(true);
    setRefreshKey((key) => key + 1);
  }

  if (loading) {
    return <p className="text-center py-20 text-gray-500">Cargando estadísticas…</p>;
  }

  const total = estadisticas?.actividadesCreadas ?? 0;
  const porEstado: { estado: TipoEstadoActividad; cantidad: number }[] = estadisticas
    ? [
        {
          estado: 'PROPUESTA',
          // Cada actividad tiene un único estado y no hay borrado: las propuestas son el resto del total.
          cantidad: Math.max(
            0,
            total -
              (estadisticas.actividadesConfirmadas +
                estadisticas.actividadesReprogramadas +
                estadisticas.actividadesCanceladas +
                estadisticas.actividadesFinalizadas),
          ),
        },
        { estado: 'CONFIRMADA', cantidad: estadisticas.actividadesConfirmadas },
        { estado: 'REPROGRAMADA', cantidad: estadisticas.actividadesReprogramadas },
        { estado: 'CANCELADA', cantidad: estadisticas.actividadesCanceladas },
        { estado: 'FINALIZADA', cantidad: estadisticas.actividadesFinalizadas },
      ]
    : [];

  return (
    <div className="space-y-10">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Estadísticas de la plataforma</h1>
          <p className="mt-1 text-sm text-gray-500">Totales globales, calculados al momento.</p>
        </div>
        <Button variant="secondary" onClick={handleActualizar} disabled={refreshing}>
          {refreshing ? 'Actualizando…' : 'Actualizar'}
        </Button>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>
      )}

      {estadisticas && (
        <section>
          <h2 className="text-2xl font-bold text-gray-900 mb-6">Actividades</h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <Card className="border-indigo-100 bg-indigo-50/30">
              <CardBody>
                <p className="text-sm font-medium text-gray-500">Actividades creadas</p>
                <p className="mt-2 text-4xl font-extrabold text-gray-900">{total}</p>
                <p className="mt-2 text-xs text-gray-500">Total histórico</p>
              </CardBody>
            </Card>

            {porEstado.map(({ estado, cantidad }) => {
              const badge = ESTADO_BADGE[estado];
              const pct = porcentaje(cantidad, total);
              return (
                <Card key={estado}>
                  <CardBody>
                    <div className="flex items-start justify-between">
                      <p className="text-sm font-medium text-gray-500">Actividades en estado</p>
                      <Badge variant={badge.variant}>{badge.label}</Badge>
                    </div>
                    <p className="mt-2 text-4xl font-extrabold text-gray-900">{cantidad}</p>
                    <div className="mt-3 h-2 w-full rounded-full bg-gray-200">
                      <div className={`h-2 rounded-full ${BARRA_COLOR[badge.variant]}`} style={{ width: `${pct}%` }} />
                    </div>
                    <p className="mt-2 text-xs text-gray-500">{pct}% del total</p>
                  </CardBody>
                </Card>
              );
            })}
          </div>
        </section>
      )}
    </div>
  );
};
