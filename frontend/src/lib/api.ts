const API_BASE_URL = import.meta.env.VITE_API_URL ?? '';

export class ApiError extends Error {
  public readonly status: number;

  constructor(
    message: string,
    status: number,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

interface ApiRequestOptions extends RequestInit {
  token?: string;
}

function isTechnicalMessage(msg: string): boolean {
  const technicalKeywords = [
    '<html>',
    '<!doctype',
    'failed to read request',
    'json parse error',
    'cannot deserialize',
    'required request body',
    'failed to convert',
    'stack trace',
    'nullpointerexception',
    'exception:',
    'at org.',
    'at com.',
    'at java.',
    'bad gateway',
    'internal server error',
    'syntaxerror',
    'unexpected token',
    'methodargument',
    'classcastexception',
    'handler dispatch failed',
    'nested exception',
    'sqlstate',
    'mongoexception',
    'timed out while waiting',
    'dangling meta character',
    'patternsyntaxexception',
    'pattern syntax',
    'regex',
  ];
  const lower = msg.toLowerCase();
  return technicalKeywords.some((keyword) => lower.includes(keyword));
}

function getFriendlyFallback(status: number): string {
  if (status >= 500) {
    if (status === 502 || status === 503 || status === 504) {
      return 'El servicio no está disponible en este momento. Verificá que los servidores estén en ejecución e intentá nuevamente.';
    }
    return 'Ocurrió un error inesperado en el servidor. Por favor, intentá nuevamente más tarde.';
  }
  if (status === 400) {
    return 'Los datos enviados no son válidos o están incompletos. Por favor, revisá la información ingresada.';
  }
  if (status === 401) {
    return 'Credenciales inválidas o tu sesión ha expirado. Por favor, volvé a iniciar sesión.';
  }
  if (status === 403) {
    return 'No tenés permisos para realizar esta acción.';
  }
  if (status === 404) {
    return 'El recurso solicitado no fue encontrado.';
  }
  if (status === 409) {
    return 'Hubo un conflicto con el estado actual del recurso. Por favor, intentá nuevamente.';
  }
  return `La solicitud no pudo ser procesada (${status}). Por favor, intentá nuevamente.`;
}

export function extractErrorMessage(body: unknown, status: number): string {
  const fallback = getFriendlyFallback(status);

  // 1. Si el body es un string, verificar si en realidad es un JSON serializado
  let parsedBody = body;
  if (typeof body === 'string' && body.trim()) {
    const trimmed = body.trim();
    if (
      (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
      (trimmed.startsWith('[') && trimmed.endsWith(']'))
    ) {
      try {
        parsedBody = JSON.parse(trimmed);
      } catch {
        parsedBody = body;
      }
    }
  }

  // 2. Si es un error 500 o mayor, nunca mostrar texto técnico crudo
  if (status >= 500) {
    if (parsedBody && typeof parsedBody === 'object') {
      const record = parsedBody as Record<string, unknown>;
      const raw = record.detail ?? record.message;
      if (typeof raw === 'string' && raw.trim() && !isTechnicalMessage(raw)) {
        return raw.trim();
      }
    }
    return fallback;
  }

  // 3. Si parsedBody es un objeto (JSON / ProblemDetail / Validation)
  if (parsedBody && typeof parsedBody === 'object') {
    const record = parsedBody as Record<string, unknown>;

    // 3a. Errores de validación de campos: { "errores": { "fechaLimite": "La fecha limite debe ser futura" } }
    if (record.errores && typeof record.errores === 'object') {
      const fieldErrors = Object.entries(record.errores as Record<string, unknown>)
        .map(([_, msg]) => (typeof msg === 'string' ? msg.trim() : ''))
        .filter((msg) => Boolean(msg) && !isTechnicalMessage(msg));

      if (fieldErrors.length > 0) {
        return fieldErrors.join('. ');
      }
    }

    // 3b. Mensajes estándar de negocio (detail / message / error)
    const candidate = record.detail ?? record.message ?? record.error;
    if (typeof candidate === 'string' && candidate.trim()) {
      const trimmed = candidate.trim();
      if (!isTechnicalMessage(trimmed)) {
        return trimmed;
      }
    }

    return fallback;
  }

  // 4. Si es un string simple no-JSON
  if (typeof parsedBody === 'string' && parsedBody.trim()) {
    const trimmed = parsedBody.trim();
    if (!trimmed.startsWith('<') && !trimmed.startsWith('{') && !isTechnicalMessage(trimmed)) {
      return trimmed;
    }
  }

  return fallback;
}

export async function apiRequest<T>(
  path: string,
  { token, headers, ...options }: ApiRequestOptions = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  });

  const contentType = response.headers.get('content-type') ?? '';
  let body: unknown = null;

  if (response.status !== 204) {
    // Acepta 'application/json', 'application/problem+json', o cualquier subtipo json
    const isJson = contentType.includes('json');
    if (isJson) {
      try {
        body = await response.json();
      } catch {
        body = await response.text();
      }
    } else {
      const text = await response.text();
      const trimmed = text.trim();
      if (
        (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
        (trimmed.startsWith('[') && trimmed.endsWith(']'))
      ) {
        try {
          body = JSON.parse(trimmed);
        } catch {
          body = text;
        }
      } else {
        body = text;
      }
    }
  }

  if (!response.ok) {
    throw new ApiError(
      extractErrorMessage(body, response.status),
      response.status,
    );
  }

  return body as T;
}
