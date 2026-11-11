/** Usuario tal como lo devuelve GET /admin/usuarios (UsuarioDTO). */
export interface UsuarioAdmin {
  idUsuario: number;
  nombre: string;
  correo: string;
  telefono?: string;
  rol: string;
  estado: string;
  /** Motivo de la restricción; solo viene si el usuario está RESTRINGIDO (CA-058). */
  motivoRestriccion?: string | null;
  fechaRegistro?: string;
}

/** Alojamiento de un anfitrión, para el detalle del usuario. */
export interface AlojamientoDeUsuario {
  idAlojamiento?: number;
  titulo: string;
  estado?: string;
}

/** Reserva de un huésped, para el detalle del usuario. */
export interface ReservaDeUsuario {
  idReserva?: number;
  estado?: string;
  fechaSalida?: string;
}

/** Tipo normalizado del rol. */
export type TipoRol = 'ADMIN' | 'ANFITRION' | 'HUESPED' | '';

/** Tipo normalizado del estado de la cuenta. */
export type TipoEstado = 'ACTIVO' | 'RESTRINGIDO' | 'INACTIVO' | '';
