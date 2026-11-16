import 'zone.js';
import {
  context,
  SpanKind,
  SpanStatusCode,
  trace,
  type Span,
} from '@opentelemetry/api';
import { ZoneContextManager } from '@opentelemetry/context-zone';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base';
import { WebTracerProvider } from '@opentelemetry/sdk-trace-web';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';

const NOMBRE_SERVICIO_FRONTEND = 'tacs-frontend';
const NOMBRE_TRACER = 'com.tacs.frontend.busqueda-actividades';

let inicializado = false;

export interface ContextoBusquedaFrontend {
  tipo: string;
  estado: string;
  conTexto: boolean;
  conFechas: boolean;
  pagina: number;
  tamano: number;
}

/**
 * Inicializa OpenTelemetry Browser únicamente para propagar traceparent y registrar
 * spans vinculados al flujo de búsqueda de actividades (GET /api/actividades).
 */
export function initTelemetry(): void {
  if (inicializado) {
    return;
  }
  inicializado = true;

  const exporter = new OTLPTraceExporter({
    url: import.meta.env.VITE_OTEL_EXPORTER_URL ?? '/otel/v1/traces',
  });

  const provider = new WebTracerProvider({
    resource: resourceFromAttributes({
      [ATTR_SERVICE_NAME]: NOMBRE_SERVICIO_FRONTEND,
    }),
    spanProcessors: [
      new BatchSpanProcessor(exporter, {
        maxQueueSize: 64,
        scheduledDelayMillis: 500,
      }),
    ],
  });

  provider.register({
    contextManager: new ZoneContextManager(),
  });

  registerInstrumentations({
    instrumentations: [
      new FetchInstrumentation({
        // Instrumenta exclusivamente requests de búsqueda de actividades (/api/actividades?...)
        ignoreUrls: [/^(?!.*\/api\/actividades\?).*$/],
        propagateTraceHeaderCorsUrls: [
          /^http:\/\/localhost(:\d+)?\//,
          /^http:\/\/127\.0\.0\.1(:\d+)?\//,
        ],
        clearTimingResources: true,
      }),
    ],
  });
}

/**
 * Ejecuta la interacción de búsqueda de actividades dentro de un span raíz en el frontend
 * que abarca desde el disparo de la búsqueda hasta tener los resultados listos para mostrar.
 */
export async function ejecutarConSpanBusqueda<T>(
  datos: ContextoBusquedaFrontend,
  operacion: (span: Span) => Promise<T>,
): Promise<T> {
  const tracer = trace.getTracer(NOMBRE_TRACER);
  const span = tracer.startSpan('actividad.buscar.frontend', {
    kind: SpanKind.CLIENT,
    attributes: {
      'actividad.filtro.tipo': datos.tipo,
      'actividad.filtro.estado': datos.estado,
      'actividad.filtro.tiene_busqueda_texto': datos.conTexto,
      'actividad.filtro.tiene_rango_fechas': datos.conFechas,
      'actividad.paginacion.pagina': datos.pagina,
      'actividad.paginacion.tamano': datos.tamano,
    },
  });

  const ctxConSpan = trace.setSpan(context.active(), span);

  try {
    const resultado = await context.with(ctxConSpan, () => operacion(span));
    span.setStatus({ code: SpanStatusCode.OK });
    return resultado;
  } catch (error) {
    const mensaje = error instanceof Error ? error.message : 'Error al buscar actividades';
    if (error instanceof Error) {
      span.recordException(error);
    }
    span.setStatus({ code: SpanStatusCode.ERROR, message: mensaje });
    throw error;
  } finally {
    span.end();
  }
}
