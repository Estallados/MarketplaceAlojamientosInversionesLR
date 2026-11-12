import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { Resena } from '../../resenas/models/resena';
import { Pagina } from '../models/publicacion-admin';
import { valorDe } from './admin-publicaciones.service';

/** Largo máximo del motivo para ocultar una reseña (LimitesTexto.MOTIVO del backend). */
export const MAX_MOTIVO_RESENA = 500;

/**
 * Moderación de reseñas del administrador (HU-14): GET /admin/resenas, PATCH /admin/resenas/{id}/ocultar
 * y PATCH /admin/resenas/{id}/mostrar. La reseña no se borra: el backend solo cambia su estado, la saca de
 * las consultas públicas y del promedio, avisa a su autor y lo registra en la auditoría.
 * El token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AdminResenasService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/admin/resenas`;

  /** Una página de reseñas (todas, las más recientes primero). */
  pagina(pagina = 0, tamanio = 100): Observable<Pagina<Resena>> {
    const params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    return this.http
      .get<Partial<Pagina<Resena>> & { content?: Resena[]; totalPages?: number }>(this.base, { params })
      .pipe(
        map((r) => ({
          contenido: r?.contenido ?? r?.content ?? [],
          pagina: r?.pagina ?? pagina,
          tamanio: r?.tamanio ?? tamanio,
          totalElementos: r?.totalElementos ?? 0,
          totalPaginas: r?.totalPaginas ?? r?.totalPages ?? 1,
        })),
      );
  }

  /** Todas las reseñas, publicadas y ocultas (páginas de 100, hasta `maxPaginas`), como en el proyecto base. */
  todas(maxPaginas = 20): Observable<Resena[]> {
    return this.pagina(0).pipe(
      switchMap((primera) => {
        const paginas = Math.min(primera.totalPaginas, maxPaginas);
        if (paginas <= 1) {
          return of(primera.contenido);
        }
        const resto = Array.from({ length: paginas - 1 }, (_, i) => this.pagina(i + 1));
        return forkJoin(resto).pipe(map((otras) => [primera.contenido, ...otras.map((p) => p.contenido)].flat()));
      }),
    );
  }

  /**
   * Oculta una reseña con un motivo (CA-066). 200 con la reseña OCULTA.
   * 400 motivo vacío o de más de 500 caracteres · 403 sin rol ADMIN (CA-069) · 404 no existe.
   */
  ocultar(id: number, motivo: string): Observable<Resena> {
    return this.http.patch<Resena>(`${this.base}/${id}/ocultar`, { motivo });
  }

  /** Vuelve a mostrar una reseña oculta. 200 con la reseña PUBLICADA · 403 sin rol ADMIN · 404 no existe. */
  mostrar(id: number): Observable<Resena> {
    return this.http.patch<Resena>(`${this.base}/${id}/mostrar`, {});
  }

  /** Mensaje legible a partir de un error HTTP del backend ({ message }). */
  mensajeDeError(error: unknown, porDefecto: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return porDefecto;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor.';
    }
    if (error.status === 403) {
      return 'No tienes permisos para realizar esta operación.';
    }
    const cuerpo: unknown = error.error;
    if (typeof cuerpo === 'string' && cuerpo.trim()) {
      return cuerpo;
    }
    return valorDe(cuerpo, 'mensaje', 'message', 'error') || porDefecto;
  }
}
