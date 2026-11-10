// ======================================
// RESERVAS (S07)
// Esquemas ReservaEntrada, Reserva, Pago y EstadoReserva de
// docs/openapi/reservas-pagos.yaml
// ======================================

/** Estados de una reserva según el contrato. */
export type EstadoReserva =
  | 'PENDIENTE_PAGO'
  | 'CONFIRMADA'
  | 'EN_REVISION'
  | 'CANCELADA';

/** Estados de un pago según el contrato. */
export type EstadoPago =
  | 'PENDIENTE'
  | 'APROBADO'
  | 'RECHAZADO'
  | 'ERROR'
  | 'REEMBOLSADO';

/**
 * Cuerpo de POST /reservas. Solo viaja la cotización: el monto lo toma el backend de ella
 * (CA-086), así que el front nunca envía un precio.
 */
export interface ReservaEntrada {

  idCotizacion: number;

  /** PaymentMethod de Stripe (HU-27); si se omite, el backend usa pm_card_visa. */
  metodoPago?: string;
}

/** Último pago de la reserva. */
export interface PagoReserva {

  idPago: number;

  valor: number;

  estado: EstadoPago;

  idReserva: number;

  referencia?: string;

  proveedor?: 'STRIPE_TEST' | 'SIMULADO';

  detalle?: string | null;

  fecha?: string;

  metodo?: string;
}

/** Reserva que devuelve POST /reservas (201). */
export interface Reserva {

  idReserva: number;

  fechaEntrada: string;

  fechaSalida: string;

  noches: number;

  huespedes: number;

  estado: EstadoReserva;

  /** Igual al total cotizado (CA-086). */
  total: number;

  idHuesped: number;

  idAlojamiento: number;

  fechaCreacion: string;

  tituloAlojamiento?: string;

  idCotizacion?: number;

  desglose?: string;

  pago?: PagoReserva | null;
}
