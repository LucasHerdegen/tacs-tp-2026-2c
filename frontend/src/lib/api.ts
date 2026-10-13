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

function extractFieldErrors(body: unknown): Record<string, string> | undefined {
  if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>;
    if (record.errores && typeof record.errores === 'object' && !Array.isArray(record.errores)) {
      const fieldErrors: Record<string, string> = {};
      for (const [key, value] of Object.entries(record.errores)) {
        if (typeof value === 'string') {
          fieldErrors[key] = value;
        }
      }
      return Object.keys(fieldErrors).length > 0 ? fieldErrors : undefined;
    }
  }
  return undefined;
}

function extractErrorMessage(body: unknown, fallback: string): string {
  if (typeof body === 'string' && body.trim()) return body;

  if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>;

    if (record.errores && typeof record.errores === 'object' && !Array.isArray(record.errores)) {
      const msgs = Object.values(record.errores).filter((msg): msg is string => typeof msg === 'string' && msg.trim().length > 0);
      if (msgs.length > 0) {
        return msgs.join('. ');
      }
    }

    const message = record.message ?? record.detail ?? record.error;
    if (typeof message === 'string' && message.trim()) return message;
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
    body = contentType.includes('application/json')
      ? await response.json()
      : await response.text();
  }

  if (!response.ok) {
    const fieldErrors = extractFieldErrors(body);
    throw new ApiError(
      extractErrorMessage(body, `La solicitud falló (${response.status})`),
      response.status,
      fieldErrors,
      body,
    );
  }

  return body as T;
}
