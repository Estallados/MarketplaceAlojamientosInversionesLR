import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import {
  ConfiguracionTarifa,
  ConfiguracionTarifaEntrada
} from '../models/configuracion-tarifa';

// ======================================
// SERVICIO DE TARIFAS (HU-24, RF-023)
// GET y PUT /admin/tarifas (ADMIN o ANFITRION)
// ======================================

/**
 * Consulta y cambia los porcentajes globales de limpieza y de servicio. No guarda caché: cada
 * consulta trae del backend los valores vigentes.
 */
@Injectable({
  providedIn: 'root'
})
export class TarifasService {

  /** Cliente HTTP. */
  private readonly http = inject(HttpClient);

  private readonly baseUrl = inject(API_URL);

  /** Ruta de la configuración de tarifas. */
  private readonly apiUrl =
    `${this.baseUrl.replace(/\/+$/, '')}/admin/tarifas`;

  // ======================================
  // 1. CONSULTAR PORCENTAJES VIGENTES
  // GET /admin/tarifas
  // ======================================

  /**
   * Porcentajes vigentes; sin configuración guardada el backend devuelve los valores por defecto
   * con {@code porDefecto} en true.
   *
   * @returns la configuración vigente
   */
  tarifas(): Observable<ConfiguracionTarifa> {

    return this.http.get<ConfiguracionTarifa>(
      this.apiUrl
    );

  }

  // ======================================
  // 2. CAMBIAR PORCENTAJES
  // PUT /admin/tarifas
  // ======================================

  /**
   * Cambia los dos porcentajes a la vez; aplican a las cotizaciones nuevas (CA-084).
   *
   * @param datos porcentajes nuevos (0 a 100)
   * @returns la configuración nueva
   */
  actualizarTarifas(
    datos: ConfiguracionTarifaEntrada
  ): Observable<ConfiguracionTarifa> {

    return this.http.put<ConfiguracionTarifa>(
      this.apiUrl,
      {
        porcentajeLimpieza: datos.porcentajeLimpieza,
        porcentajeServicio: datos.porcentajeServicio
      }
    );

  }

}
