/** Estados posibles de una publicación de alojamiento. */
export type EstadoPublicacion = 'BORRADOR' | 'PENDIENTE_REVISION' | 'ACTIVA' | 'PAUSADA' | 'BLOQUEADA';

/** Servicio adicional con costo que ofrece el alojamiento. */
export interface ServicioAdicionalAdmin {
  idServicio?: number;
  nombre: string;
  costo: number;
}

/**
 * Publicación de alojamiento tal como la ve el administrador (AlojamientoDTO del backend).
 * Solo incluye los campos que usan las pantallas de revisión (HU-32).
 */
export interface PublicacionAdmin {
  idAlojamiento?: number;
  titulo: string;
  descripcion?: string;
  ubicacion?: string;
  ciudad?: string;
  tipo?: string;
  capacidad?: number;
  precioNoche?: number;
  habitaciones?: number;
  camas?: number;
  banos?: number;
  amenidades?: string[];
  serviciosAdicionales?: ServicioAdicionalAdmin[];
  estado?: EstadoPublicacion | string;
  idAnfitrion?: number;
  nombreAnfitrion?: string;
  correoAnfitrion?: string;
  fechaCreacion?: string;
  imagenes?: string[];
  promedioCalificacion?: number | null;
  cantidadResenas?: number;
}

/** Un cambio de estado de la publicación (HistorialEstadoAlojamientoDTO). */
export interface HistorialPublicacion {
  idHistorial?: number;
  estadoAnterior?: string | null;
  estadoNuevo?: string;
  motivo?: string;
  fecha?: string;
}

/** Página que devuelve el backend: elementos y totales. */
export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}
