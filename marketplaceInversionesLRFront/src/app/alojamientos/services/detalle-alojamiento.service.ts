import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { DetalleAlojamiento, ResumenResenasDetalle } from '../models/detalle-alojamiento';

/**
 * Consulta del detalle de un alojamiento (HU-18).
 * GET /alojamientos/{id} y GET /alojamientos/{id}/resenas.
 */
@Injectable({ providedIn: 'root' })
export class DetalleAlojamientoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/alojamientos`;

  /** Datos completos del alojamiento. */
  obtener(id: number): Observable<DetalleAlojamiento> {
    return this.http.get<DetalleAlojamiento>(`${this.base}/${id}`);
  }

  /** Reseñas publicadas, promedio y cantidad. */
  resenas(id: number): Observable<ResumenResenasDetalle> {
    return this.http.get<ResumenResenasDetalle>(`${this.base}/${id}/resenas`);
  }
}
