// Conversión entre el formato que espera <input type="datetime-local">
// (YYYY-MM-DDTHH:mm) y los ISO LocalDateTime que devuelve/espera el backend.

export function toDatetimeLocalValue(isoString: string): string {
  // "2026-10-01T18:57:18.194" -> "2026-10-01T18:57"
  return isoString.slice(0, 16);
}

export function formatFechaHora(isoString: string): string {
  return new Date(isoString).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' });
}

export function fromDatetimeLocalValue(value: string): string {
  // El input da "2026-10-01T18:57" (sin segundos); el backend espera
  // LocalDateTime, que acepta bien un ISO con o sin segundos.
  return value;
}