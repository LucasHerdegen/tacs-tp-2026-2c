import type { TipoProveedorClima } from '../auth/types';

export type EstadoProveedorClima = 'OK' | 'DEGRADADO' | 'CAIDO';

export interface PuntoEstadoProveedorDto {
  timestamp: string;
  estado: EstadoProveedorClima;
  detalle: string;
}

export interface EstadisticasProveedorClimaDto {
  proveedor: TipoProveedorClima;
  nombre: string;
  estadoActual: EstadoProveedorClima;
  estadoCircuitBreaker: string;
  llamadasTotales: number;
  llamadasExitosas: number;
  erroresTotales: number;
  respuestasInvalidas: number;
  timeouts: number;
  rechazosRateLimit: number;
  fallbacksActivados: number;
  cacheHits: number;
  cacheMisses: number;
  cacheHitRatio: number;
  historialEstado: PuntoEstadoProveedorDto[];
}

export interface EstadisticasDto {
  actividadesCreadas: number;
  actividadesReprogramadas: number;
  actividadesCanceladas: number;
  actividadesConfirmadas: number;
  actividadesFinalizadas: number;
  proveedoresClima?: EstadisticasProveedorClimaDto[];
}
