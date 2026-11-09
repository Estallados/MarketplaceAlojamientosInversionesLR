import { EstadoAlojamiento } from './alojamiento';

// ======================================
// DISPONIBILIDAD DEL ALOJAMIENTO (HU-20)
// Esquemas de docs/openapi/disponibilidad-busqueda.yaml
// ======================================

/** Período en que el alojamiento se ofrece. */
export interface PeriodoDisponibilidad {

  /** Identificador del período. */
  idPeriodo: number;

  /** Alojamiento. */
  idAlojamiento: number;

  /** Primer día de llegada. */
  fechaInicio: string;

  /** Último día de salida. */
  fechaFin: string;
}

// Datos necesarios para crear o actualizar
// un período en Spring Boot.

/** Fechas para crear o modificar un período. */
export interface DatosPeriodoDisponibilidad {

  /** Primer día de llegada. */
  fechaInicio: string;

  /** Último día de salida. */
  fechaFin: string;
}



/** Rango ocupado por una reserva. */
export interface RangoReservado {

  /** Día de llegada. */
  fechaEntrada: string;

  /** Día de salida. */
  fechaSalida: string;
}

/** Calendario de un alojamiento: períodos ofrecidos y rangos reservados. */
export interface CalendarioDisponibilidad {

  /** Alojamiento. */
  idAlojamiento: number;

  /** Estado de la publicación. */
  estadoAlojamiento: EstadoAlojamiento;

  /** Períodos ofrecidos. */
  periodos: PeriodoDisponibilidad[];

  /** Rangos ocupados. */
  reservados: RangoReservado[];
}
