import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_URL } from '../../services/api-url';
import {
  AlojamientoRespuesta,
  DatosAlojamiento,
  ImagenAlojamiento,
  PoliticaCancelacion
} from '../models/alojamiento';

// ======================================
// SERVICIO DE PUBLICACIONES DEL ANFITRIÓN
// docs/openapi/alojamientos.yaml
// El token JWT lo agrega authInterceptor.
// ======================================

@Injectable({
  providedIn: 'root'
})
export class PublicacionHostService {

  private readonly http = inject(HttpClient);

  private readonly baseUrl = inject(API_URL);

  /** Ruta base de los alojamientos. */
  private readonly apiUrl =
    `${this.baseUrl.replace(/\/+$/, '')}/alojamientos`;

  // ====================================
  // 1. POLÍTICAS DE CANCELACIÓN
  // GET /alojamientos/politicas
  // ====================================

  obtenerPoliticas(): Observable<PoliticaCancelacion[]> {

    return this.http.get<PoliticaCancelacion[]>(
      `${this.apiUrl}/politicas`
    );
  }

  // ====================================
  // 2. CREAR BORRADOR
  // POST /alojamientos (multipart/form-data)
  // ====================================

  /**
   * Crea el alojamiento; el backend lo deja en BORRADOR (CA-094).
   *
   * @param datos JSON del alojamiento (campo 'datos')
   * @param imagenes de 1 a 15 imágenes (campo 'archivos'); la primera queda como principal
   */
  crearBorrador(
    datos: DatosAlojamiento,
    imagenes: File[]
  ): Observable<AlojamientoRespuesta> {

    const formData = new FormData();

    // JSON del alojamiento
    formData.append(
      'datos',
      JSON.stringify(datos)
    );

    // Fotografías
    for (const imagen of imagenes) {

      formData.append(
        'archivos',
        imagen,
        imagen.name
      );
    }

    // No establecer Content-Type manualmente.
    // El navegador genera el multipart boundary.

    return this.http.post<AlojamientoRespuesta>(
      this.apiUrl,
      formData
    );
  }

  // ====================================
  // 3. ACTUALIZAR ALOJAMIENTO
  // PUT /alojamientos/{id}
  // ====================================

  actualizarBorrador(
    id: number,
    datos: DatosAlojamiento
  ): Observable<AlojamientoRespuesta> {

    return this.http.put<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}`,
      datos
    );
  }

  // ====================================
  // 4. AGREGAR FOTOGRAFÍAS
  // POST /alojamientos/{id}/imagenes
  // ====================================

  agregarImagenes(
    id: number,
    imagenes: File[]
  ): Observable<ImagenAlojamiento[]> {

    const formData = new FormData();

    for (const imagen of imagenes) {

      formData.append(
        'archivos',
        imagen,
        imagen.name
      );
    }

    return this.http.post<ImagenAlojamiento[]>(
      `${this.apiUrl}/${id}/imagenes`,
      formData
    );
  }

  // ====================================
  // 5. ENVIAR A REVISIÓN
  // POST /alojamientos/{id}/enviarrevision
  // ====================================

  enviarRevision(
    id: number
  ): Observable<AlojamientoRespuesta> {

    return this.http.post<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}/enviarrevision`,
      {}
    );
  }
}
