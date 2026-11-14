import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { Dashboard } from '../models/dashboard';

/**
 * Dashboard administrativo (HU-04): GET /admin/dashboard?topCiudades=.
 * El backend ya entrega los indicadores calculados; el token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AdminDashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/admin/dashboard`;

  /** Indicadores de usuarios, publicaciones, reservas, ingresos y ciudades con más demanda (CA-022). */
  obtener(topCiudades = 5): Observable<Dashboard> {
    return this.http.get<Dashboard>(this.base, { params: new HttpParams().set('topCiudades', topCiudades) });
  }
}
