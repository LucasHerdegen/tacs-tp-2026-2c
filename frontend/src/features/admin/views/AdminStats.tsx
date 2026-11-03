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
import type {
  EstadisticasDto,
  EstadisticasProveedorClimaDto,
  EstadoProveedorClima,
} from '../types';

const BARRA_COLOR: Record<BadgeVariant, string> = {
  success: 'bg-green-500',
  warning: 'bg-yellow-500',
  error: 'bg-red-500',
  info: 'bg-indigo-500',
  neutral: 'bg-gray-400',
};

const ESTADO_PROVEEDOR_BADGE: Record<
  EstadoProveedorClima,
  { label: string; variant: BadgeVariant }
> = {
  OK: { label: 'OK · Operativo', variant: 'success' },
  DEGRADADO: { label: 'DEGRADADO', variant: 'warning' },
  CAIDO: { label: 'CAÍDO', variant: 'error' },
};

function porcentaje(cantidad: number, total: number): number {
  return total === 0 ? 0 : Math.round((cantidad / total) * 100);
}

function badgeCircuitBreaker(estado: string): BadgeVariant {
  if (estado === 'CLOSED') return 'success';
  if (estado === 'HALF_OPEN') return 'warning';
  return 'error';
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

  const proveedores: EstadisticasProveedorClimaDto[] =
    estadisticas?.proveedoresClima ?? [];

  return (
    <div className="space-y-10">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Estadísticas de la plataforma</h1>
          <p className="mt-1 text-sm text-gray-500">Totales globales y telemetría de resiliencia en tiempo real.</p>
        </div>
        <Button variant="secondary" onClick={handleActualizar} disabled={refreshing}>
          {refreshing ? 'Actualizando…' : 'Actualizar'}
        </Button>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>
      )}

      {estadisticas && (
        <>
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

          {proveedores.length > 0 && (
            <section>
              <div className="mb-6">
                <h2 className="text-2xl font-bold text-gray-900">Proveedores de Clima</h2>
                <p className="text-sm text-gray-500 mt-1">
                  Estado de salud, Circuit Breaker, tasa de aciertos de caché, errores y fallbacks por proveedor.
                </p>
              </div>

              <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                {proveedores.map((prov) => {
                  const badgeSalud =
                    ESTADO_PROVEEDOR_BADGE[prov.estadoActual] ?? ESTADO_PROVEEDOR_BADGE.OK;
                  return (
                    <Card key={prov.proveedor} className="border-gray-200">
                      <CardBody className="space-y-5">
                        <div className="flex flex-wrap items-start justify-between gap-2">
                          <div>
                            <span className="text-xs font-semibold uppercase tracking-wider text-indigo-600">
                              {prov.proveedor}
                            </span>
                            <h3 className="text-lg font-bold text-gray-900">{prov.nombre}</h3>
                          </div>
                          <div className="flex items-center gap-2">
                            <Badge variant={badgeSalud.variant}>{badgeSalud.label}</Badge>
                            <Badge variant={badgeCircuitBreaker(prov.estadoCircuitBreaker)}>
                              CB: {prov.estadoCircuitBreaker}
                            </Badge>
                          </div>
                        </div>

                        {/* Cache Hit Ratio */}
                        <div className="rounded-xl bg-gray-50 p-3.5 border border-gray-200/80">
                          <div className="flex justify-between text-xs font-semibold text-gray-700 mb-1.5">
                            <span>Cache Hit Ratio</span>
                            <span>
                              {prov.cacheHitRatio}% ({prov.cacheHits} hits / {prov.cacheMisses} misses)
                            </span>
                          </div>
                          <div className="h-2.5 w-full rounded-full bg-gray-200">
                            <div
                              className="h-2.5 rounded-full bg-indigo-600 transition-all"
                              style={{ width: `${Math.min(100, Math.max(0, prov.cacheHitRatio))}%` }}
                            />
                          </div>
                        </div>

                        {/* Contadores de llamadas y errores */}
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 text-center">
                          <div className="rounded-lg bg-white border border-gray-200 p-2.5">
                            <p className="text-[11px] text-gray-500 font-medium">Llamadas</p>
                            <p className="text-xl font-bold text-gray-900">{prov.llamadasTotales}</p>
                          </div>
                          <div className="rounded-lg bg-emerald-50/60 border border-emerald-200 p-2.5">
                            <p className="text-[11px] text-emerald-700 font-medium">Exitosas</p>
                            <p className="text-xl font-bold text-emerald-900">{prov.llamadasExitosas}</p>
                          </div>
                          <div className="rounded-lg bg-rose-50/60 border border-rose-200 p-2.5">
                            <p className="text-[11px] text-rose-700 font-medium">Errores</p>
                            <p className="text-xl font-bold text-rose-900">{prov.erroresTotales}</p>
                          </div>
                          <div className="rounded-lg bg-amber-50/60 border border-amber-200 p-2.5">
                            <p className="text-[11px] text-amber-800 font-medium">Fallbacks</p>
                            <p className="text-xl font-bold text-amber-900">{prov.fallbacksActivados}</p>
                          </div>
                        </div>

                        {/* Desglose de fallas */}
                        <div className="grid grid-cols-3 gap-2 text-xs text-gray-600 bg-gray-50 rounded-lg p-2.5 border border-gray-100">
                          <div>
                            <span className="font-semibold text-gray-900">{prov.respuestasInvalidas}</span>{' '}
                            malformadas
                          </div>
                          <div>
                            <span className="font-semibold text-gray-900">{prov.timeouts}</span>{' '}
                            timeouts
                          </div>
                          <div>
                            <span className="font-semibold text-gray-900">{prov.rechazosRateLimit}</span>{' '}
                            rate-limit
                          </div>
                        </div>

                        {/* Historial de estados */}
                        <div>
                          <p className="text-xs font-bold uppercase tracking-wider text-gray-500 mb-2">
                            Historial de estado ({prov.historialEstado.length})
                          </p>
                          <div className="max-h-36 overflow-y-auto space-y-1.5 pr-1">
                            {prov.historialEstado
                              .slice()
                              .reverse()
                              .map((punto, idx) => {
                                const b =
                                  ESTADO_PROVEEDOR_BADGE[punto.estado] ?? ESTADO_PROVEEDOR_BADGE.OK;
                                return (
                                  <div
                                    key={idx}
                                    className="flex items-center justify-between gap-2 text-xs bg-gray-50 rounded-lg px-2.5 py-1.5 border border-gray-100"
                                  >
                                    <div className="flex items-center gap-2 truncate">
                                      <Badge variant={b.variant} className="text-[10px] px-1.5 py-0">
                                        {punto.estado}
                                      </Badge>
                                      <span className="text-gray-700 truncate" title={punto.detalle}>
                                        {punto.detalle}
                                      </span>
                                    </div>
                                    <span className="text-[10px] text-gray-400 shrink-0">
                                      {new Date(punto.timestamp).toLocaleTimeString('es-AR', {
                                        hour: '2-digit',
                                        minute: '2-digit',
                                        second: '2-digit',
                                      })}
                                    </span>
                                  </div>
                                );
                              })}
                          </div>
                        </div>
                      </CardBody>
                    </Card>
                  );
                })}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
};
