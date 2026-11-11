/** Reserva tal como la devuelve GET /admin/usuarios/{id}/reservas (ReservaDTO). */
export interface ReservaAdmin {
  idReserva: number;
  fechaEntrada: string;
  fechaSalida: string;
  noches?: number;
  huespedes?: number;
  estado: string;
  total?: number;
  idHuesped?: number;
  idAlojamiento?: number;
  tituloAlojamiento?: string;
}

/** Reserva con los datos que muestra la tabla del administrador. */
export interface ReservaAdminVista extends ReservaAdmin {
  nombreHuesped: string;
  nombreAnfitrion: string;
  ciudad: string;
  /** Confirmada y con la fecha de salida ya pasada. */
  completada: boolean;
}
