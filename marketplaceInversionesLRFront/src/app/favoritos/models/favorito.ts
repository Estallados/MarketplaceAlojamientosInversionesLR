import { AlojamientoRespuesta } from '../../alojamientos/models/alojamiento';

// ======================================
// FAVORITOS (HU-29)
// Respuestas de /favoritos (FavoritoController, FavoritoDTO y PaginaDTO del backend)
// ======================================

/** Alojamiento guardado por el usuario (FavoritoDTO). */
export interface Favorito {
  idFavorito: number;
  /** Momento en que se agregó, en formato ISO. */
  fechaAgregado: string;
  idUsuario: number;
  idAlojamiento: number;
  /** Datos del alojamiento para mostrar la tarjeta. */
  alojamiento?: AlojamientoRespuesta | null;
}

/** Página de favoritos (PaginaDTO), los más recientes primero. */
export interface PaginaFavoritos {
  contenido: Favorito[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}
