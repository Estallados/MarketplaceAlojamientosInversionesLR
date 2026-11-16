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
  pagina?: number;
  totalElementos?: number;
  totalPaginas?: number;
}

/** Opciones de los filtros de Explorar (GET /alojamientos/filtros) para el conjunto que se explora. */
export interface FiltrosExplorar {
  tipos: string[];
  amenidades: string[];
  precioMinimo: number | null;
  precioMaximo: number | null;
}

/** Filtros y orden que se envían al pedir una página de Explorar; los vacíos no se envían. */
export interface ConsultaExplorar {
  tipo?: string;
  precioMinimo?: number;
  precioMaximo?: number;
  amenidades?: string[];
  orden?: string;
}
