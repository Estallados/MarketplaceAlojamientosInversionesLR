/** Alojamiento que aparece en los resultados de búsqueda (AlojamientoDTO, solo lo que usa la tarjeta). */
export interface AlojamientoResultado {
  idAlojamiento?: number;
  titulo: string;
  ciudad?: string;
  tipo?: string;
  capacidad?: number;
  precioNoche?: number;
  amenidades?: string[];
  imagenes?: string[];
  promedioCalificacion?: number | null;
  cantidadResenas?: number;
}

/** Campos de la barra de búsqueda, tal como los escribe el usuario. */
export interface CamposBusqueda {
  ciudad: string;
  entrada: string;
  salida: string;
  huespedes: string;
}

/** Página que devuelve el backend. */
export interface PaginaResultados {
  contenido?: AlojamientoResultado[];
  content?: AlojamientoResultado[];
}
