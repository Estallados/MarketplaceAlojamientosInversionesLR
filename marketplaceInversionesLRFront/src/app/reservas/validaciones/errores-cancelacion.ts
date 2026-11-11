import { HttpErrorResponse } from '@angular/common/http';

// ======================================
// ERRORES DE LA CANCELACIÓN (HU-01)
// Mensajes de GET /reservas/{id}/simularcancelacion y POST /reservas/{id}/cancelar.
// El backend responde { "message": "…" } con el motivo exacto (por ejemplo «Solo se pueden
// cancelar reservas futuras.»); se muestra tal cual y solo se usa un texto propio cuando no viene.
// ======================================

/** Largo máximo del motivo de la cancelación (LimitesTexto.MOTIVO del backend). */
export const MAX_MOTIVO_CANCELACION = 500;

/** Mensaje legible para un error al simular o cancelar. */
export function mensajeErrorCancelacion(error: unknown, porDefecto: string): string {

  const http = error instanceof HttpErrorResponse ? error : null;

  if (http?.status === 0) {
    return 'No hay conexión con el servidor.';
  }

  const cuerpo = http?.error as { message?: unknown } | string | null | undefined;

  if (typeof cuerpo === 'string' && cuerpo.trim()) {
    return cuerpo;
  }
  if (cuerpo && typeof cuerpo === 'object' && typeof cuerpo.message === 'string' && cuerpo.message.trim()) {
    return cuerpo.message;
  }

  switch (http?.status) {
    case 401:
      return 'Tu sesión expiró. Inicia sesión de nuevo.';
    case 403:
      return 'Solo el huésped de la reserva puede cancelarla.';
    case 404:
      return 'La reserva no existe o no tienes acceso a ella.';
    case 409:
      return 'La reserva ya no se puede cancelar.';
    case 422:
      return 'La solicitud de cancelación cambió; inténtalo de nuevo.';
    default:
      return porDefecto;
  }
}
