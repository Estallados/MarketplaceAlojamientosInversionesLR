import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import {
  AlojamientoRespuesta,
  DatosAlojamiento,
  ImagenAlojamiento,
  MensajeServidor,
  PaginaAlojamientos,
  PoliticaCancelacion,
  ServicioAdicional,
  ServicioAdicionalEntrada
} from '../models/alojamiento';
import {
  CalendarioDisponibilidad,
  DatosPeriodoDisponibilidad,
  PeriodoDisponibilidad
} from '../models/disponibilidad';

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

    return this.actualizarAlojamiento(id, datos);
  }

  /**
   * Edita la publicación (HU-16). Solo el anfitrión propietario o un
   * ADMIN; cualquier otro recibe 403 (CA-101, CA-113, CA-114).
   * Los campos que no se envían no cambian.
   *
   * @returns el alojamiento actualizado (CA-100)
   */
  actualizarAlojamiento(
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
  // 4.1 ELIMINAR FOTOGRAFÍA (HU-16)
  // DELETE /alojamientos/{id}/imagenes/{idImagen}
  // 200: imágenes restantes, la principal primero
  // 409: debe quedar al menos una imagen
  // ====================================

  eliminarImagen(
    idAlojamiento: number,
    idImagen: number
  ): Observable<ImagenAlojamiento[]> {

    return this.http.delete<ImagenAlojamiento[]>(
      `${this.apiUrl}/${idAlojamiento}/imagenes/${idImagen}`
    );
  }

  // ====================================
  // 4.2 MARCAR FOTOGRAFÍA PRINCIPAL (HU-16)
  // PATCH /alojamientos/{id}/imagenes/{idImagen}/principal
  // 200: imágenes, la principal primero
  // ====================================

  marcarImagenPrincipal(
    idAlojamiento: number,
    idImagen: number
  ): Observable<ImagenAlojamiento[]> {

    return this.http.patch<ImagenAlojamiento[]>(
      `${this.apiUrl}/${idAlojamiento}/imagenes/${idImagen}/principal`,
      {}
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

  // ====================================
  // 5.1 PAUSAR Y REANUDAR (HU-17)
  // POST /alojamientos/{id}/pausar    ACTIVA → PAUSADA
  // POST /alojamientos/{id}/reanudar  PAUSADA → ACTIVA
  // Solo ANFITRION; 409 si el estado no permite la transición.
  // ====================================

  /** Pausa la publicación: deja de recibir reservas nuevas (CA-108). */
  pausar(
    id: number
  ): Observable<AlojamientoRespuesta> {

    return this.http.post<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}/pausar`,
      {}
    );
  }

  /** Reanuda una publicación PAUSADA; una BLOQUEADA no se puede reanudar. */
  reanudar(
    id: number
  ): Observable<AlojamientoRespuesta> {

    return this.http.post<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}/reanudar`,
      {}
    );
  }

  // ====================================
  // 6. DETALLE DEL ALOJAMIENTO
  // GET /alojamientos/{id}
  // ====================================

  /**
   * Detalle con imágenes y servicios adicionales. Si no está ACTIVA,
   * solo lo ven su anfitrión o un ADMIN (para los demás responde 404).
   */
  obtenerAlojamiento(
    id: number
  ): Observable<AlojamientoRespuesta> {

    return this.http.get<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}`
    );
  }

  // ====================================
  // 7. SERVICIOS ADICIONALES (CA-185)
  // POST   /alojamientos/{id}/servicios
  // PUT    /alojamientos/{id}/servicios/{idServicio}
  // DELETE /alojamientos/{id}/servicios/{idServicio}
  // Aplican a cotizaciones nuevas; el backend no
  // altera las reservas ya confirmadas.
  // ====================================

  agregarServicio(
    idAlojamiento: number,
    servicio: ServicioAdicionalEntrada
  ): Observable<ServicioAdicional> {

    return this.http.post<ServicioAdicional>(
      `${this.apiUrl}/${idAlojamiento}/servicios`,
      servicio
    );
  }

  actualizarServicio(
    idAlojamiento: number,
    idServicio: number,
    servicio: ServicioAdicionalEntrada
  ): Observable<ServicioAdicional> {

    return this.http.put<ServicioAdicional>(
      `${this.apiUrl}/${idAlojamiento}/servicios/${idServicio}`,
      servicio
    );
  }

  eliminarServicio(
    idAlojamiento: number,
    idServicio: number
  ): Observable<MensajeServidor> {

    return this.http.delete<MensajeServidor>(
      `${this.apiUrl}/${idAlojamiento}/servicios/${idServicio}`
    );
  }

  // ====================================
  // 8. AGREGAR FECHAS DE DISPONIBILIDAD
  // POST /alojamientos/{id}/disponibilidad
  // ====================================

  /**
   * POST /alojamientos/{id}/disponibilidad. Registra un período.
   *
   * @param idAlojamiento alojamiento
   * @param periodo fechas del período
   * @returns el período creado
   */
  agregarPeriodoDisponibilidad(
    idAlojamiento: number,
    periodo: DatosPeriodoDisponibilidad
  ): Observable<PeriodoDisponibilidad> {

    return this.http.post<PeriodoDisponibilidad>(
      `${this.apiUrl}/${idAlojamiento}/disponibilidad`,
      periodo
    );
  }

  // ====================================
  // 9. CONSULTAR DISPONIBILIDAD
  // GET /alojamientos/{id}/disponibilidad
  // ====================================

  /**
   * GET /alojamientos/{id}/disponibilidad. Calendario del alojamiento.
   *
   * @param idAlojamiento alojamiento
   * @returns el calendario
   */
  obtenerDisponibilidad(
    idAlojamiento: number
  ): Observable<CalendarioDisponibilidad> {

    return this.http.get<CalendarioDisponibilidad>(
      `${this.apiUrl}/${idAlojamiento}/disponibilidad`
    );
  }

  // ====================================
  // 10. ACTUALIZAR PERÍODO
  // PUT /alojamientos/{id}/disponibilidad/{idPeriodo}
  // ====================================

  /**
   * PUT /alojamientos/{id}/disponibilidad/{idPeriodo}. Modifica un período.
   *
   * @param idAlojamiento alojamiento
   * @param idPeriodo período
   * @param periodo fechas nuevas
   * @returns el período actualizado
   */
  actualizarPeriodoDisponibilidad(
    idAlojamiento: number,
    idPeriodo: number,
    periodo: DatosPeriodoDisponibilidad
  ): Observable<PeriodoDisponibilidad> {

    return this.http.put<PeriodoDisponibilidad>(
      `${this.apiUrl}/${idAlojamiento}/disponibilidad/${idPeriodo}`,
      periodo
    );
  }

  // ====================================
  // 11. ELIMINAR PERÍODO
  // DELETE /alojamientos/{id}/disponibilidad/{idPeriodo}
  // ====================================

  /**
   * DELETE /alojamientos/{id}/disponibilidad/{idPeriodo}. Elimina un período sin reservas.
   *
   * @param idAlojamiento alojamiento
   * @param idPeriodo período
   * @returns la respuesta con el mensaje
   */
  eliminarPeriodoDisponibilidad(
    idAlojamiento: number,
    idPeriodo: number
  ): Observable<MensajeServidor> {

    return this.http.delete<MensajeServidor>(
      `${this.apiUrl}/${idAlojamiento}/disponibilidad/${idPeriodo}`
    );
  }

  // ====================================
  // 12. MIS ALOJAMIENTOS (HU-19, CA-103)
  // GET /alojamientos/mios?pagina=&tamanio=
  // Solo ANFITRION (403 para los demás). Los más recientes primero.
  // ====================================

  /**
   * Publicaciones del anfitrión de la sesión, con el estado de cada una.
   *
   * @param pagina número de página (desde 0)
   * @param tamanio elementos por página
   */
  obtenerMisAlojamientos(
    pagina = 0,
    tamanio = 20
  ): Observable<PaginaAlojamientos> {

    return this.http.get<PaginaAlojamientos>(
      `${this.apiUrl}/mios`,
      { params: { pagina, tamanio } }
    );
  }
}
