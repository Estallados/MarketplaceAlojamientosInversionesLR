import { HttpErrorResponse } from '@angular/common/http';

// ======================================
// ERRORES DE COTIZAR Y RESERVAR (HU-22)
// Códigos de POST /cotizaciones y POST /reservas en
// docs/openapi/reservas-pagos.yaml. Los errores solo traen
// {"message": "..."} (esquema Mensaje), sin un código tipado: el 409
// tiene varias causas y se distinguen por el texto del backend
// (ReservaService, CotizacionService y CalendarioService).
// ======================================

/**
 * Causa del rechazo:
 * - no-activo: el alojamiento no está ACTIVA (CA-071).
 * - fechas-ocupadas: otra reserva tomó el período o ya no está libre (CA-072, CA-074).
 * - cotizacion-vencida: la cotización venció o ya se usó.
 * - en-proceso: otra petición con la misma clave sigue en proceso.
 * - clave-reutilizada: la Idempotency-Key se usó con otra petición (422).
 * - pago-rechazado: el pago no se confirmó (402 o 502); la reserva quedó CANCELADA.
 * - validacion, sesion, permiso, no-encontrado: 400, 401, 403 y 404.
 * - conexion: no hubo respuesta; no se sabe si la reserva se creó.
 * - servidor: error 5xx; la respuesta no se guarda y se puede reintentar con la misma clave.
 */
export type TipoErrorReserva =
  | 'no-activo'
  | 'fechas-ocupadas'
  | 'cotizacion-vencida'
  | 'en-proceso'
  | 'clave-reutilizada'
  | 'pago-rechazado'
  | 'validacion'
  | 'sesion'
  | 'permiso'
  | 'no-encontrado'
  | 'conexion'
  | 'servidor'
  | 'desconocido';

export interface ErrorReserva {

  tipo: TipoErrorReserva;

  /** Mensaje para el huésped. */
  mensaje: string;

  /** Mensaje del backend, si lo envió. */
  detalle: string;
}

/** Tipos en que el mismo intento se puede repetir con la misma Idempotency-Key. */
export const TIPOS_REINTENTABLES: readonly TipoErrorReserva[] = [
  'conexion',
  'servidor',
  'en-proceso'
];

/** Texto del campo message del cuerpo de error, si viene. */
function mensajeServidor(err: HttpErrorResponse): string {

  const cuerpo = err.error as unknown;

  if (cuerpo && typeof cuerpo === 'object' && 'message' in cuerpo) {
    const message = (cuerpo as { message?: unknown }).message;
    return typeof message === 'string' ? message.trim() : '';
  }

  return typeof cuerpo === 'string' ? cuerpo.trim() : '';
}

/** Texto en minúsculas y sin tildes, para comparar con los mensajes del backend. */
function normalizar(texto: string): string {

  return texto
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');
}

/** Causa del 409 según el mensaje del backend. */
function tipoConflicto(detalle: string): TipoErrorReserva {

  const texto = normalizar(detalle);

  // "El alojamiento no está disponible para nuevas reservas (estado PAUSADA)."
  if (texto.includes('nuevas reservas')) {
    return 'no-activo';
  }

  // "La cotización venció o ya se usó; solicita una cotización nueva."
  if (texto.includes('cotizacion')) {
    return 'cotizacion-vencida';
  }

  // "La petición con esa clave sigue en proceso."
  if (texto.includes('en proceso')) {
    return 'en-proceso';
  }

  // "El período ya no está disponible: otra reserva lo tomó."
  // "El alojamiento no está disponible en esas fechas."
  return 'fechas-ocupadas';
}

const MENSAJES: Record<TipoErrorReserva, string> = {
  'no-activo':
    'Este alojamiento no está disponible para reservar en este momento.',
  'fechas-ocupadas':
    'Las fechas seleccionadas dejaron de estar disponibles. Elige otras fechas.',
  'cotizacion-vencida':
    'La cotización venció o ya se usó. Solicita una cotización nueva para reservar.',
  'en-proceso':
    'Tu solicitud de reserva todavía se está procesando. Espera un momento y vuelve a intentarlo.',
  'clave-reutilizada':
    'No se pudo procesar la solicitud porque se repitió con datos diferentes. Vuelve a intentarlo.',
  'pago-rechazado':
    'El pago no se confirmó y la reserva no quedó pagada.',
  'validacion':
    'Los datos de la reserva no son válidos.',
  'sesion':
    'Tu sesión venció. Inicia sesión de nuevo para reservar.',
  'permiso':
    'No tienes permiso para reservar este alojamiento.',
  'no-encontrado':
    'La cotización o el alojamiento ya no existe.',
  'conexion':
    'No se pudo conectar con el servidor. No sabemos si la reserva se creó: ' +
    'reintenta y no se duplicará.',
  'servidor':
    'El servidor no pudo completar la operación. Inténtalo de nuevo.',
  'desconocido':
    'Ocurrió un error inesperado. Inténtalo de nuevo.'
};

/**
 * Interpreta un error de POST /cotizaciones o POST /reservas.
 *
 * @param err error recibido
 */
export function clasificarErrorReserva(err: unknown): ErrorReserva {

  if (!(err instanceof HttpErrorResponse)) {
    return { tipo: 'desconocido', mensaje: MENSAJES.desconocido, detalle: '' };
  }

  const detalle = mensajeServidor(err);

  const tipo = tipoPorEstado(err.status, detalle);

  return { tipo, mensaje: MENSAJES[tipo], detalle };
}

function tipoPorEstado(status: number, detalle: string): TipoErrorReserva {

  switch (status) {
    case 0:
      return 'conexion';
    case 400:
      return 'validacion';
    case 401:
      return 'sesion';
    case 402:
      return 'pago-rechazado';
    case 403:
      return 'permiso';
    case 404:
      return 'no-encontrado';
    case 409:
      return tipoConflicto(detalle);
    case 422:
      return 'clave-reutilizada';
    case 502:
      return 'pago-rechazado';
    default:
      return status >= 500 ? 'servidor' : 'desconocido';
  }
}
