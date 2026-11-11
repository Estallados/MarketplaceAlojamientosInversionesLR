import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { ConfiguracionPago } from '../models/pago';

// ======================================
// SERVICIO DE PAGOS (HU-27)
// GET /pagos/configuracion: público. El cobro se hace dentro de POST /reservas.
// ======================================

@Injectable({
  providedIn: 'root'
})
export class PagoService {

  private readonly http = inject(HttpClient);

  private readonly apiUrl =
    `${inject(API_URL).replace(/\/+$/, '')}/pagos`;

  /** Modo de pago (STRIPE_TEST o SIMULADO), moneda y métodos de prueba con su resultado. */
  configuracion(): Observable<ConfiguracionPago> {

    return this.http.get<ConfiguracionPago>(`${this.apiUrl}/configuracion`);
  }
}
