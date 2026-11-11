/** Notificación dentro de la plataforma tal como la devuelve GET /notificaciones (NotificacionDTO). */
export interface Notificacion {
  idNotificacion: number;
  /** Evento que la originó, por ejemplo RESERVA_CONFIRMADA (CA-010). */
  tipo: string;
  /** Texto que ve el usuario. */
  mensaje: string;
  /** Fecha y hora en que se generó (ISO). */
  fecha: string;
  leida: boolean;
  idUsuario?: number;
}

/** Título que se muestra para cada tipo de notificación. */
export const TITULOS_NOTIFICACION: Record<string, string> = {
  RESERVA_CONFIRMADA: 'Reserva confirmada',
  RESERVA_CANCELADA: 'Reserva cancelada',
  RESERVA_EN_REVISION: 'Reserva en revisión',
  PAGO_NO_CONFIRMADO: 'Pago no confirmado',
  ALOJAMIENTO_APROBADO: 'Publicación aprobada',
  ALOJAMIENTO_RECHAZADO: 'Publicación rechazada',
  ALOJAMIENTO_BLOQUEADO: 'Publicación bloqueada',
  RESENA_RECIBIDA: 'Nueva reseña',
  RESENA_OCULTA: 'Reseña oculta',
  CUENTA_RESTRINGIDA: 'Cuenta restringida',
  CUENTA_LIBERADA: 'Cuenta reactivada',
};

/** Título de una notificación según su tipo. */
export function tituloNotificacion(tipo: string | null | undefined): string {
  return (tipo && TITULOS_NOTIFICACION[tipo]) || 'Notificación';
}
