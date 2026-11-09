// ======================================
// CONSULTA DE DISPONIBILIDAD (HU-21)
// Esquema ConsultaDisponibilidad de docs/openapi/disponibilidad-busqueda.yaml
// ======================================

/** Respuesta de GET /alojamientos/{idAlojamiento}/disponibilidad/consultar. */
export interface ConsultaDisponibilidad {

  idAlojamiento: number;

  /** Llegada consultada (AAAA-MM-DD). */
  fechaEntrada: string;

  /** Salida consultada (AAAA-MM-DD). */
  fechaSalida: string;

  /** true si está libre (CA-119); false si incluye fechas reservadas o no disponibles (CA-120). */
  disponible: boolean;

  /** Motivo cuando no está disponible. */
  motivo?: string | null;
}

/** Fechas de la estadía que se pasan al siguiente paso (cotización o reserva). */
export interface FechasEstadia {

  idAlojamiento: number;

  /** AAAA-MM-DD */
  llegada: string;

  /** AAAA-MM-DD */
  salida: string;
}
