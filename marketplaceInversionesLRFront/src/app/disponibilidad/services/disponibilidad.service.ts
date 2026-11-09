import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { ConsultaDisponibilidad } from '../models/consulta-disponibilidad';

// ======================================
// SERVICIO DE DISPONIBILIDAD (S04)
// docs/openapi/disponibilidad-busqueda.yaml
// La consulta es pública; si hay sesión, authInterceptor agrega el token.
// ======================================

@Injectable({
  providedIn: 'root'
})
export class DisponibilidadService {

  private readonly http = inject(HttpClient);

  private readonly baseUrl = inject(API_URL);

  private readonly apiUrl =
    `${this.baseUrl.replace(/\/+$/, '')}/alojamientos`;

  // ====================================
  // CONSULTAR DISPONIBILIDAD (HU-21)
  // GET /alojamientos/{idAlojamiento}/disponibilidad/consultar?llegada=&salida=
  // 200: { disponible, motivo } (CA-119, CA-120)
  // 400: fechas inválidas (CA-082) · 404: alojamiento inexistente o no visible
  // ====================================

  /**
   * @param idAlojamiento alojamiento
   * @param llegada fecha de llegada en formato AAAA-MM-DD
   * @param salida fecha de salida en formato AAAA-MM-DD, posterior a la llegada
   */
  consultar(
    idAlojamiento: number,
    llegada: string,
    salida: string
  ): Observable<ConsultaDisponibilidad> {

    const params = new HttpParams()
      .set('llegada', llegada)
      .set('salida', salida);

    return this.http.get<ConsultaDisponibilidad>(
      `${this.apiUrl}/${idAlojamiento}/disponibilidad/consultar`,
      { params }
    );
  }
}
