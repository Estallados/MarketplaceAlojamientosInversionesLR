import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { NuevaResena, Resena, ResumenResenas } from '../models/resena';

/**
 * Reseñas (HU-03): POST /resenas, GET /resenas/mias (huésped) y GET /resenas/anfitrion (anfitrión).
 * Las reseñas públicas de un alojamiento las muestra el detalle (GET /alojamientos/{id}/resenas).
 * El token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class ResenaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/resenas`;

  /**
   * Reseña una estancia completada (201). Una reseña por reserva (CA-016, CA-018).
   * 400 datos inválidos · 404 reserva inexistente · 409 no completada o ya reseñada.
   */
  crear(datos: NuevaResena): Observable<Resena> {
    return this.http.post<Resena>(this.base, datos);
  }

  /** Mis reseñas como huésped, incluidas las ocultas por moderación, las más recientes primero. */
  mias(tamanio = 50): Observable<Resena[]> {
    const params = new HttpParams().set('pagina', 0).set('tamanio', tamanio);
    return this.http
      .get<{ contenido?: Resena[]; content?: Resena[] }>(`${this.base}/mias`, { params })
      .pipe(map((r) => r?.contenido ?? r?.content ?? []));
  }

  /** Reseñas publicadas de mis alojamientos (o de uno) con promedio y cantidad (CA-019). */
  deAnfitrion(pagina: number, tamanio: number, idAlojamiento?: number): Observable<ResumenResenas> {
    let params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    if (idAlojamiento != null) {
      params = params.set('idAlojamiento', idAlojamiento);
    }
    return this.http.get<ResumenResenas>(`${this.base}/anfitrion`, { params });
  }

  /** Mensaje legible a partir de un error HTTP del backend. */
  mensajeDeError(error: unknown, porDefecto: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return porDefecto;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor.';
    }
    const cuerpo: unknown = error.error;
    if (typeof cuerpo === 'string' && cuerpo.trim()) {
      return cuerpo;
    }
    if (cuerpo !== null && typeof cuerpo === 'object') {
      const r = cuerpo as { mensaje?: string; message?: string };
      if (r.mensaje || r.message) {
        return (r.mensaje || r.message) as string;
      }
    }
    if (error.status === 403) {
      return 'No tienes permisos para esta operación.';
    }
    if (error.status === 404) {
      return 'El alojamiento seleccionado no existe o no te pertenece.';
    }
    return porDefecto;
  }
}
