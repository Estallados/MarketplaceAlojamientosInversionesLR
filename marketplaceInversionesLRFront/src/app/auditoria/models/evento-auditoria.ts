/** Resultado de un evento de auditoría (enum ResultadoAuditoria del backend). */
export type ResultadoAuditoria = 'EXITOSO' | 'RECHAZADO' | 'ERROR';

/** Evento de auditoría tal como lo devuelve GET /admin/auditoria (EventoAuditoriaDTO). */
export interface EventoAuditoria {
  idEvento: number;
  /** Acción realizada, por ejemplo LOGIN. */
  accion: string;
  /** Fecha y hora del evento (ISO). */
  fecha: string;
  detalle?: string | null;
  /** Usuario que hizo la acción; vacío si no se conoce. */
  idUsuario?: number | null;
  /** Tipo de recurso afectado, por ejemplo USUARIO. */
  entidad?: string | null;
  idEntidad?: number | null;
  resultado: ResultadoAuditoria | string;
}

/** Filtros opcionales de la consulta (CA-062). desde/hasta en ISO date-time. */
export interface FiltrosAuditoria {
  accion?: string;
  idActor?: number;
  desde?: string;
  hasta?: string;
}

/** Página que devuelve el backend. */
export interface PaginaAuditoria {
  contenido: EventoAuditoria[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}
