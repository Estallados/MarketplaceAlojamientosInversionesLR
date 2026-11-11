// ======================================
// COTIZACIÓN DE UNA ESTANCIA (S06)
// Esquemas CotizacionEntrada y Cotizacion de docs/openapi/reservas-pagos.yaml
// Todos los valores los calcula el backend; el front solo los muestra.
// ======================================

/** Datos que manda el huésped en POST /cotizaciones. */
export interface CotizacionEntrada {

  idAlojamiento: number;

  /** Día de llegada (AAAA-MM-DD). */
  fechaEntrada: string;

  /** Día de salida (AAAA-MM-DD), posterior a la llegada. */
  fechaSalida: string;

  /** Cantidad de huéspedes; si se omite, el backend usa 1. */
  huespedes?: number;

  /** Servicios adicionales elegidos (HU-31). */
  serviciosSeleccionados?: number[];
}

/** Servicio adicional incluido en la cotización (esquema ServicioAdicional). */
export interface ServicioCotizado {

  idServicio: number;

  idAlojamiento?: number;

  nombre: string;

  costo: number;
}

/** Desglose que devuelve POST /cotizaciones (CA-179). Sirve 30 minutos para reservar. */
export interface Cotizacion {

  /** Se envía en POST /reservas (CA-070, CA-086). */
  idCotizacion: number;

  idAlojamiento: number;

  fechaEntrada: string;

  fechaSalida: string;

  huespedes: number;

  noches: number;

  precioNoche: number;

  precioBase: number;

  descuento: number;

  porcentajeLimpieza: number;

  valorLimpieza: number;

  porcentajeServicio: number;

  valorServicio: number;

  valorAdicionales: number;

  /** Valor total a pagar. */
  total: number;

  serviciosSeleccionados?: number[];

  /** Servicios elegidos que entraron en el total (CA-183). */
  serviciosAdicionales?: ServicioCotizado[];

  /** Todos los servicios adicionales que ofrece el alojamiento (HU-31). */
  serviciosDisponibles?: ServicioCotizado[];

  /** Momento en que deja de servir para reservar; llega sin zona horaria. */
  fechaExpiracion: string;

  /** Reserva creada con esta cotización; null si aún no se usa. */
  idReserva?: number | null;

  idUsuario?: number;

  fechaCreacion?: string;
}
