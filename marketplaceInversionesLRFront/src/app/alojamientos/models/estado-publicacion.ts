import { EstadoAlojamiento } from './alojamiento';

// ======================================
// ESTADOS DE LA PUBLICACIÓN (HU-17)
// docs/openapi/alojamientos.yaml: esquema EstadoAlojamiento y tag Estados
//
// La interfaz muestra etiquetas amigables, pero las peticiones y
// comparaciones usan siempre el valor que envía el backend (CA-104).
// ======================================

/** Los cinco estados del contrato, en el orden del ciclo de vida (CA-104). */
export const ESTADOS_ALOJAMIENTO: readonly EstadoAlojamiento[] = [
  'BORRADOR',
  'PENDIENTE_REVISION',
  'ACTIVA',
  'PAUSADA',
  'BLOQUEADA'
];

/** Indica si el valor es uno de los cinco estados del contrato (CA-104). */
export function esEstadoAlojamiento(
  valor: unknown
): valor is EstadoAlojamiento {

  return (
    typeof valor === 'string' &&
    (ESTADOS_ALOJAMIENTO as readonly string[]).includes(valor)
  );
}

// ======================================
// TRANSICIONES DEL ANFITRIÓN
// POST /alojamientos/{id}/enviarrevision  BORRADOR → PENDIENTE_REVISION
// POST /alojamientos/{id}/pausar          ACTIVA → PAUSADA
// POST /alojamientos/{id}/reanudar        PAUSADA → ACTIVA
// Aprobar, rechazar y bloquear son del administrador (HU-32).
// ======================================

export type AccionEstado =
  | 'enviarRevision'
  | 'pausar'
  | 'reanudar';

export const TRANSICIONES: Record<
  AccionEstado,
  { desde: EstadoAlojamiento; hacia: EstadoAlojamiento }
> = {
  enviarRevision: { desde: 'BORRADOR', hacia: 'PENDIENTE_REVISION' },
  pausar: { desde: 'ACTIVA', hacia: 'PAUSADA' },
  reanudar: { desde: 'PAUSADA', hacia: 'ACTIVA' }
};

/**
 * Acciones que el anfitrión puede pedir desde el estado actual.
 * Un estado desconocido no ofrece ninguna (CA-104).
 */
export function accionesPermitidas(
  estado: unknown
): AccionEstado[] {

  if (!esEstadoAlojamiento(estado)) {
    return [];
  }

  return (Object.keys(TRANSICIONES) as AccionEstado[]).filter(
    accion => TRANSICIONES[accion].desde === estado
  );
}

/**
 * Solo una publicación ACTIVA se muestra disponible para nuevas reservas
 * (CA-108, CA-159). Es una indicación visual: quien impide la reserva es
 * el backend (ReservaService y CotizacionService rechazan si no está ACTIVA).
 */
export function aceptaNuevasReservas(estado: unknown): boolean {
  return estado === 'ACTIVA';
}

// ======================================
// PRESENTACIÓN
// ======================================

export interface PresentacionEstado {

  /** Etiqueta para el usuario; no se envía al backend. */
  etiqueta: string;

  /** Qué significa el estado para el anfitrión. */
  descripcion: string;

  /** Colores de la insignia (Tailwind). */
  clases: string;
}

const PRESENTACION: Record<EstadoAlojamiento, PresentacionEstado> = {

  BORRADOR: {
    etiqueta: 'Borrador',
    descripcion:
      'La publicación todavía no es visible para los huéspedes. ' +
      'Completa sus datos y envíala a revisión.',
    clases: 'bg-slate-100 text-slate-700'
  },

  PENDIENTE_REVISION: {
    etiqueta: 'Pendiente de revisión',
    descripcion:
      'Un administrador debe aprobarla antes de que sea visible ' +
      'y pueda recibir reservas.',
    clases: 'bg-amber-50 text-amber-800'
  },

  ACTIVA: {
    etiqueta: 'Activa',
    descripcion:
      'La publicación es visible y está disponible para nuevas reservas.',
    clases: 'bg-emerald-100 text-emerald-800'
  },

  PAUSADA: {
    etiqueta: 'Pausada',
    descripcion:
      'No está disponible para nuevas reservas. ' +
      'Las reservas ya confirmadas se mantienen.',
    clases: 'bg-blue-50 text-blue-700'
  },

  BLOQUEADA: {
    etiqueta: 'Bloqueada',
    descripcion:
      'Bloqueada por administración: no está disponible para nuevas ' +
      'reservas y no se puede reanudar.',
    clases: 'bg-red-50 text-red-700'
  }
};

const DESCONOCIDO: PresentacionEstado = {
  etiqueta: 'Estado no reconocido',
  descripcion:
    'El servidor envió un estado que la aplicación no reconoce. ' +
    'No se ofrecen acciones sobre la publicación.',
  clases: 'bg-slate-100 text-slate-500'
};

/** Etiqueta, descripción y colores del estado; neutros si es desconocido. */
export function presentacionEstado(
  estado: unknown
): PresentacionEstado {

  return esEstadoAlojamiento(estado)
    ? PRESENTACION[estado]
    : DESCONOCIDO;
}
