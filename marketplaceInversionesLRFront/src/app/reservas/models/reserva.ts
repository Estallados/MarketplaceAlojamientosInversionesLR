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

  /** STRIPE_TEST o SIMULADO: el pago nunca es un cobro real (CA-160). */
  proveedor?: 'STRIPE_TEST' | 'SIMULADO' | string;

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

  // ---- Datos para consultar el historial (HU-25, GET /reservas/mias, /anfitrion y /{id}) ----

  /** true cuando la estadía ya terminó. */
  completada?: boolean;

  imagenAlojamiento?: string | null;

  ciudad?: string | null;

  /** Dirección; el backend la comparte con la reserva confirmada. */
  ubicacion?: string | null;

  tipoAlojamiento?: string | null;

  capacidadAlojamiento?: number | null;

  nombrePolitica?: string | null;

  nombreHuesped?: string | null;

  idAnfitrion?: number | null;

  nombreAnfitrion?: string | null;

  tieneResena?: boolean;

  /** Cancelación registrada, si la reserva se canceló (HU-01). */
  cancelacion?: CancelacionReserva | null;
}

/** Cancelación de una reserva (CancelacionDTO del backend). */
export interface CancelacionReserva {
  idCancelacion?: number;
  motivo?: string | null;
  politica?: string | null;
  porcentajeReembolso?: number | null;
  valorReembolso?: number | null;
  estado?: 'SOLICITADA' | 'APROBADA' | 'RECHAZADA' | 'COMPLETADA' | string;
  estadoFinalReserva?: EstadoReserva;
  fecha?: string | null;
  idUsuario?: number;
  idReserva?: number;
}

/**
 * Lo que se devolvería si la reserva se cancelara hoy (SimulacionCancelacionDTO,
 * GET /reservas/{id}/simularcancelacion, HU-01 CA-001). El backend aplica la política del
 * alojamiento; el front solo lo muestra.
 */
export interface SimulacionCancelacion {
  idReserva: number;
  /** Nombre de la política: FLEXIBLE, MODERADA o ESTRICTA. */
  politica: string;
  diasAnticipacion: number;
  porcentajeReembolso: number;
  valorReembolso: number;
  totalPagado: number;
}

/** Página de reservas (PaginaDTO), las más recientes primero. */
export interface PaginaReservas {
  contenido: Reserva[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}
