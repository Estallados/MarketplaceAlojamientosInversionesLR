import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { Cotizacion, CotizacionEntrada } from '../models/cotizacion';

// ======================================
// SERVICIO DE COTIZACIÓN (S06)
// POST /cotizaciones de docs/openapi/reservas-pagos.yaml
// Exige sesión; authInterceptor agrega el token.
// ======================================

@Injectable({
  providedIn: 'root'
})
export class CotizacionService {

  private readonly http = inject(HttpClient);

  private readonly baseUrl = inject(API_URL);

  private readonly apiUrl =
    `${this.baseUrl.replace(/\/+$/, '')}/cotizaciones`;

  // ====================================
  // COTIZAR UNA ESTANCIA
  // POST /cotizaciones
  // 201: Cotizacion · 400: datos inválidos · 401: sin sesión
  // 404: alojamiento inexistente · 409: no ACTIVA (CA-071) o fechas no libres
  // ====================================

  /**
   * Pide al backend el desglose de la estancia. Cada llamada crea una cotización nueva que sirve
   * 30 minutos.
   *
   * @param entrada alojamiento, fechas y huéspedes
   */
  cotizar(entrada: CotizacionEntrada): Observable<Cotizacion> {

    return this.http.post<Cotizacion>(this.apiUrl, entrada);
  }
}
