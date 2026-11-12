/** Estados de una reseña (EstadoResena del backend). */
export type EstadoResena = 'PUBLICADA' | 'OCULTA';

/** Reseña tal como la devuelve el backend (ResenaDTO). */
export interface Resena {
  idResena: number;
  calificacion: number;
  comentario?: string | null;
  estado?: EstadoResena | string;
  fecha: string;
  idUsuario?: number;
  idReserva?: number;
  idAlojamiento?: number;
  nombreUsuario?: string;
  tituloAlojamiento?: string;
}

/** Datos para publicar una reseña (ResenaEntradaDTO). */
export interface NuevaResena {
  idReserva: number;
  calificacion: number;
  comentario?: string;
}

/** Página del backend. */
export interface PaginaResenas {
  contenido: Resena[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

/** Reseñas visibles con su promedio y cantidad (ResumenResenasDTO). */
export interface ResumenResenas {
  idAlojamiento: number | null;
  /** Promedio con una cifra decimal; null si no hay reseñas visibles. */
  promedio: number | null;
  cantidad: number;
  resenas: PaginaResenas;
}

/** Calificación mínima y máxima, y largo máximo del comentario (iguales al backend). */
export const CALIFICACION_MINIMA = 1;
export const CALIFICACION_MAXIMA = 5;
export const MAX_COMENTARIO = 2000;
