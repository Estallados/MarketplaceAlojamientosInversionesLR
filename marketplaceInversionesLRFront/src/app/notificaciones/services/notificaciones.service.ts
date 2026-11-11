import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { Notificacion } from '../models/notificacion';

/**
 * Notificaciones del usuario (HU-02): GET /notificaciones, GET /notificaciones/noleidas,
 * PATCH /notificaciones/leidas y PATCH /notificaciones/{id}/leida.
 * Cada usuario ve y marca solo las suyas (CA-013); el token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/notificaciones`;

  /** Contador de sin leer que muestra la campana; lo comparten la barra y la página. */
  readonly sinLeer = signal(0);

  /** Mis notificaciones, de la más reciente a la más antigua. */
  listar(tamanio = 50): Observable<Notificacion[]> {
    const params = new HttpParams().set('pagina', 0).set('tamanio', tamanio);
    return this.http
      .get<{ contenido?: Notificacion[]; content?: Notificacion[] }>(this.base, { params })
      .pipe(map((r) => r?.contenido ?? r?.content ?? []));
  }

  /** Cantidad de notificaciones sin leer. */
  noLeidas(): Observable<number> {
    return this.http.get<{ cantidad: number }>(`${this.base}/noleidas`).pipe(map((r) => r?.cantidad ?? 0));
  }

  /** Vuelve a consultar el contador de la campana; un fallo puntual no cambia el valor. */
  actualizarContador(): void {
    this.noLeidas().subscribe({
      next: (cantidad) => this.sinLeer.set(cantidad),
      error: () => undefined,
    });
  }

  /** Marca todas como leídas; devuelve cuántas se actualizaron. */
  marcarTodasLeidas(): Observable<number> {
    return this.http.patch<{ actualizadas: number }>(`${this.base}/leidas`, {}).pipe(
      map((r) => r?.actualizadas ?? 0),
      tap(() => this.sinLeer.set(0)),
    );
  }

  /** Marca una notificación propia como leída (404 si no existe o es de otro usuario). */
  marcarLeida(id: number): Observable<Notificacion> {
    return this.http
      .patch<Notificacion>(`${this.base}/${id}/leida`, {})
      .pipe(tap(() => this.sinLeer.update((n) => Math.max(0, n - 1))));
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
    return porDefecto;
  }
}
