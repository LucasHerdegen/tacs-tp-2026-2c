const API_BASE_URL = import.meta.env.VITE_API_URL ?? '';

export class ApiError extends Error {
  public readonly status: number;
  public readonly fieldErrors?: Record<string, string>;
  public readonly data?: unknown;

  constructor(
    message: string,
    status: number,
    fieldErrors?: Record<string, string>,
    data?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.data = data;
  }
}

interface ApiRequestOptions extends RequestInit {
  token?: string;
}

function parseJsonSafely(body: unknown): unknown {
  if (typeof body === 'string' && body.trim()) {
    const trimmed = body.trim();
    if (
      (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
      (trimmed.startsWith('[') && trimmed.endsWith(']'))
    ) {
      try {
        return JSON.parse(trimmed);
      } catch {
        return body;
      }
    }
  }
  return body;
}

export function extractFieldErrors(body: unknown): Record<string, string> | undefined {
  const parsed = parseJsonSafely(body);
  if (parsed && typeof parsed === 'object') {
    const record = parsed as Record<string, unknown>;
    if (record.errores && typeof record.errores === 'object' && !Array.isArray(record.errores)) {
      const fieldErrors: Record<string, string> = {};
      for (const [key, value] of Object.entries(record.errores)) {
        if (typeof value === 'string' && value.trim()) {
          fieldErrors[key] = value.trim();
        }
      }
      return Object.keys(fieldErrors).length > 0 ? fieldErrors : undefined;
    }
  }
  return undefined;
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
  const parsedBody = parseJsonSafely(body);

  // 1. Si es un error 500 o mayor, nunca mostrar texto técnico crudo
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

  // 2. Si parsedBody es un objeto (JSON / ProblemDetail / Validation)
  if (parsedBody && typeof parsedBody === 'object') {
    const record = parsedBody as Record<string, unknown>;

    // 2a. Errores de validación de campos: { "errores": { "fechaLimite": "La fecha limite debe ser futura" } }
    const fieldErrors = extractFieldErrors(parsedBody);
    if (fieldErrors) {
      const cleanErrors = Object.values(fieldErrors).filter(
        (msg) => !isTechnicalMessage(msg),
      );
      if (cleanErrors.length > 0) {
        return cleanErrors.join('. ');
      }
    }

    // 2b. Mensajes estándar de negocio (detail / message / error)
    const candidate = record.detail ?? record.message ?? record.error;
    if (typeof candidate === 'string' && candidate.trim()) {
      const trimmed = candidate.trim();
      if (!isTechnicalMessage(trimmed)) {
        return trimmed;
      }
    }

    return fallback;
  }

  // 3. Si es un string simple no-JSON
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
    const fieldErrors = extractFieldErrors(body);
    throw new ApiError(
      extractErrorMessage(body, response.status),
      response.status,
      fieldErrors,
      body,
    );
  }

  return body as T;
}
