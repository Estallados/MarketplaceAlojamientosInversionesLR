/** Total y cantidad por estado (ConteoPorEstadoDTO). */
export interface ConteoPorEstado {
  total: number;
  /** Cantidad por cada estado; el backend envía todos los estados, con 0 si no hay. */
  porEstado: Record<string, number>;
  /** Solo en reservas: confirmadas cuya salida ya pasó. */
  confirmadasFinalizadas?: number;
}

/** Reservas de una ciudad (DemandaCiudadDTO). */
export interface DemandaCiudad {
  ciudad: string;
  totalReservas: number;
}

/** Indicadores del dashboard administrativo tal como los devuelve GET /admin/dashboard (DashboardDTO). */
export interface Dashboard {
  totalUsuarios: number;
  alojamientos: ConteoPorEstado;
  reservas: ConteoPorEstado;
  /** Pagos cobrados menos las devoluciones de las cancelaciones (COP). */
  ingresosEstimados: number;
  /** Ciudades con más reservas, de mayor a menor. */
  ciudadesTop: DemandaCiudad[];
}
