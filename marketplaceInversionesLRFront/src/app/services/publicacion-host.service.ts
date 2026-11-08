
import { Injectable, inject } from '@angular/core';

import {
  HttpClient,
  HttpHeaders,
  HttpParams
} from '@angular/common/http';

import { Observable } from 'rxjs';

import { SessionService } from './session.service';



/** Estados de una publicación. */
export type EstadoAlojamiento =
  | 'BORRADOR'
  | 'PENDIENTE_REVISION'
  | 'ACTIVA'
  | 'PAUSADA'
  | 'BLOQUEADA';



/** Datos que el anfitrión envía al crear o editar su alojamiento. */
export interface DatosAlojamiento {

  /** Título de la publicación. */
  titulo: string;

  /** Descripción. */
  descripcion?: string;
  /** Dirección o referencia. */
  ubicacion?: string;
  /** Ciudad. */
  ciudad?: string;
  /** Tipo de alojamiento. */
  tipo?: string;

  /** Latitud para el mapa. */
  latitud?: number;
  /** Longitud para el mapa. */
  longitud?: number;

  /** Cantidad máxima de huéspedes. */
  capacidad?: number;
  /** Cantidad de habitaciones. */
  habitaciones?: number;
  /** Cantidad de camas. */
  camas?: number;
  /** Cantidad de baños. */
  banos?: number;

  /** Precio por noche. */
  precioNoche?: number;

  /** Porcentaje propio de limpieza. */
  porcentajeLimpieza?: number;
  /** Porcentaje propio de servicio. */
  porcentajeServicio?: number;

  /** Hora de entrada, 'HH:mm'. */
  horaEntrada?: string;
  /** Hora de salida, 'HH:mm'. */
  horaSalida?: string;

  /** Reglas de la casa. */
  reglas?: string;

  /** Política de cancelación. */
  idPolitica?: number;

  /** Amenidades. */
  amenidades?: string[];
}



/** Imagen de un alojamiento. */
export interface ImagenAlojamiento {

  /** Identificador de la imagen. */
  idImagen: number;

  /** Dirección pública de la imagen. */
  url: string;

  /** Alojamiento al que pertenece. */
  idAlojamiento?: number;

  /** Indica si es la portada. */
  principal: boolean;
}



/** Servicio adicional con costo. */
export interface ServicioAdicional {

  /** Identificador del servicio. */
  idServicio?: number;

  /** Alojamiento que lo ofrece. */
  idAlojamiento?: number;

  /** Nombre del servicio. */
  nombre: string;

  /** Costo por reserva. */
  costo: number;
}



/**
 * Alojamiento tal como lo devuelve el backend: los datos enviados más los que calcula el servidor.
 */
export interface AlojamientoRespuesta
  extends DatosAlojamiento {

  /** Identificador del alojamiento. */
  idAlojamiento: number;

  /** Estado de la publicación. */
  estado: EstadoAlojamiento;

  /** Anfitrión dueño. */
  idAnfitrion?: number;

  /** Nombre del anfitrión. */
  nombreAnfitrion?: string;

  /** Nombre de la política de cancelación. */
  nombrePolitica?: string;

  /** URLs de las imágenes, la portada primero. */
  imagenes?: string[];

  /** Imágenes con su id y si son portada. */
  detalleImagenes?: ImagenAlojamiento[];

  /** Servicios adicionales. */
  serviciosAdicionales?: ServicioAdicional[];

  /** Fecha de creación. */
  fechaCreacion?: string;

  /** Última edición. */
  fechaActualizacion?: string;

  /** Calificación promedio; null si no tiene reseñas. */
  promedioCalificacion?: number | null;

  /** Cantidad de reseñas visibles. */
  cantidadResenas?: number;

  /** Cantidad de reservas no canceladas. */
  cantidadReservas?: number;
}



/** Política de cancelación. */
export interface PoliticaCancelacion {

  /** Identificador de la política. */
  idPolitica: number;

  /** Nombre: FLEXIBLE, MODERADA o ESTRICTA. */
  nombre: string;

  /** Explicación para el huésped. */
  condiciones: string;

  /** Días mínimos para devolver el 100 %. */
  diasReembolsoTotal?: number;

  /** Días mínimos para la devolución parcial. */
  diasReembolsoParcial?: number;

  /** Porcentaje de la devolución parcial. */
  porcentajeParcial?: number;
}


/** Página de alojamientos del anfitrión. */
export interface PaginaAlojamientos {

  /** Alojamientos de la página. */
  contenido: AlojamientoRespuesta[];

  /** Número de la página, desde 0. */
  pagina: number;

  /** Elementos por página. */
  tamanio: number;

  /** Total de alojamientos. */
  totalElementos: number;

  /** Total de páginas. */
  totalPaginas: number;
}



/** Un cambio de estado de un alojamiento. */
export interface HistorialAlojamiento {

  /** Identificador del registro. */
  idHistorial: number;

  /** Alojamiento. */
  idAlojamiento: number;

  /** Estado anterior; null al crearlo. */
  estadoAnterior: EstadoAlojamiento | null;

  /** Estado nuevo. */
  estadoNuevo: EstadoAlojamiento;

  /** Motivo del cambio. */
  motivo: string | null;

  /** Usuario que hizo el cambio. */
  idUsuario: number;

  /** Momento del cambio. */
  fecha: string;
}



/** Período en que el alojamiento se ofrece. */
export interface PeriodoDisponibilidad {

  /** Identificador del período. */
  idPeriodo: number;

  /** Alojamiento. */
  idAlojamiento: number;

  /** Primer día de llegada. */
  fechaInicio: string;

  /** Último día de salida. */
  fechaFin: string;
}

// Datos necesarios para crear o actualizar
// un período en Spring Boot.

/** Fechas para crear o modificar un período. */
export interface DatosPeriodoDisponibilidad {

  /** Primer día de llegada. */
  fechaInicio: string;

  /** Último día de salida. */
  fechaFin: string;
}



/** Rango ocupado por una reserva. */
export interface RangoReservado {

  /** Día de llegada. */
  fechaEntrada: string;

  /** Día de salida. */
  fechaSalida: string;
}

/** Calendario de un alojamiento: períodos ofrecidos y rangos reservados. */
export interface CalendarioDisponibilidad {

  /** Alojamiento. */
  idAlojamiento: number;

  /** Estado de la publicación. */
  estadoAlojamiento: EstadoAlojamiento;

  /** Períodos ofrecidos. */
  periodos: PeriodoDisponibilidad[];

  /** Rangos ocupados. */
  reservados: RangoReservado[];
}



/**
 * Operaciones del anfitrión sobre sus publicaciones: crear el borrador, editarlo, manejar las
 * imágenes, enviarlo a revisión, pausarlo o reanudarlo, y administrar su disponibilidad.
 */
@Injectable({
  providedIn: 'root'
})
export class PublicacionHostService {



  /** Cliente HTTP. */
  private readonly http = inject(HttpClient);

  /** Sesión, para obtener el token. */
  private readonly sesion = inject(SessionService);



  /** Ruta base de los alojamientos. */
  private readonly apiUrl =
    'http://localhost:8081/alojamientos';

  // ====================================
  // 1. OBTENER JWT
  // ====================================

  /**
   * Cabeceras con el token de la sesión.
   *
   * @returns opciones de la petición HTTP
   */
  private opciones(): {
    headers: HttpHeaders;
  } {

    const token = this.sesion.obtenerToken();

    return {

      headers: token
        ? new HttpHeaders({
          Authorization: `Bearer ${token}`
        })
        : new HttpHeaders()

    };
  }

  // ====================================
  // 2. CONSULTAR POLÍTICAS
  // GET /alojamientos/politicas
  // ====================================

  /**
   * GET /alojamientos/politicas. Políticas de cancelación para el formulario.
   *
   * @returns las políticas
   */
  obtenerPoliticas(): Observable<PoliticaCancelacion[]> {

    return this.http.get<PoliticaCancelacion[]>(
      `${this.apiUrl}/politicas`
    );
  }

  // ====================================
  // 3. CREAR BORRADOR
  // POST /alojamientos
  // ====================================

  /**
   * POST /alojamientos. Crea el alojamiento en BORRADOR con sus imágenes.
   *
   * @param datos datos del alojamiento
   * @param imagenes imágenes (la primera queda como portada)
   * @returns el alojamiento creado
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
      formData,
      this.opciones()
    );
  }

  // ====================================
  // 4. ACTUALIZAR ALOJAMIENTO
  // PUT /alojamientos/{id}
  // ====================================

  /**
   * PUT /alojamientos/{id}. Edita los datos del alojamiento.
   *
   * @param id alojamiento
   * @param datos datos nuevos
   * @returns el alojamiento actualizado
   */
  actualizarBorrador(
    id: number,
    datos: DatosAlojamiento
  ): Observable<AlojamientoRespuesta> {

    return this.http.put<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}`,
      datos,
      this.opciones()
    );
  }

  // ====================================
  // 5. AGREGAR FOTOGRAFÍAS
  // POST /alojamientos/{id}/imagenes
  // ====================================

  /**
   * POST /alojamientos/{id}/imagenes. Sube imágenes nuevas.
   *
   * @param id alojamiento
   * @param imagenes imágenes a subir
   * @returns todas las imágenes del alojamiento
   */
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
      formData,
      this.opciones()
    );
  }

  // ====================================
  // 6. ENVIAR A REVISIÓN
  // POST /alojamientos/{id}/enviarrevision
  // ====================================

  /**
   * POST /alojamientos/{id}/enviarrevision. Envía el borrador a revisión.
   *
   * @param id alojamiento
   * @returns el alojamiento en PENDIENTE_REVISION
   */
  enviarRevision(
    id: number
  ): Observable<AlojamientoRespuesta> {

    return this.http.post<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}/enviarrevision`,
      {},
      this.opciones()
    );
  }

  // ====================================
  // 7. CONSULTAR MIS ALOJAMIENTOS
  // GET /alojamientos/mios
  // ====================================

  /**
   * GET /alojamientos/mios. Alojamientos del anfitrión.
   *
   * @param pagina número de página
   * @param tamanio elementos por página
   * @returns la página de alojamientos
   */
  obtenerMisAlojamientos(
    pagina: number = 0,
    tamanio: number = 100
  ): Observable<PaginaAlojamientos> {

    const params = new HttpParams()
      .set('pagina', pagina.toString())
      .set('tamanio', tamanio.toString());

    return this.http.get<PaginaAlojamientos>(
      `${this.apiUrl}/mios`,
      {
        ...this.opciones(),
        params
      }
    );
  }

  // ====================================
  // 8. OBTENER DETALLE
  // GET /alojamientos/{id}
  // ====================================

  /**
   * GET /alojamientos/{id}. Detalle de un alojamiento propio.
   *
   * @param id alojamiento
   * @returns el alojamiento
   */
  obtenerAlojamiento(
    id: number
  ): Observable<AlojamientoRespuesta> {

    return this.http.get<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}`,
      this.opciones()
    );
  }

  // ====================================
  // 9. OBTENER HISTORIAL
  // GET /alojamientos/{id}/historial
  // ====================================

  /**
   * GET /alojamientos/{id}/historial. Cambios de estado del alojamiento.
   *
   * @param id alojamiento
   * @returns el historial
   */
  obtenerHistorial(
    id: number
  ): Observable<HistorialAlojamiento[]> {

    return this.http.get<HistorialAlojamiento[]>(
      `${this.apiUrl}/${id}/historial`,
      this.opciones()
    );
  }

  // ====================================
  // 10. PAUSAR ALOJAMIENTO
  // POST /alojamientos/{id}/pausar
  // ====================================

  /**
   * POST /alojamientos/{id}/pausar. Pausa la publicación.
   *
   * @param id alojamiento
   * @returns el alojamiento en PAUSADA
   */
  pausarAlojamiento(
    id: number
  ): Observable<AlojamientoRespuesta> {

    return this.http.post<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}/pausar`,
      {},
      this.opciones()
    );
  }

  // ====================================
  // 11. REANUDAR ALOJAMIENTO
  // POST /alojamientos/{id}/reanudar
  // ====================================

  /**
   * POST /alojamientos/{id}/reanudar. Vuelve a publicar el alojamiento.
   *
   * @param id alojamiento
   * @returns el alojamiento en ACTIVA
   */
  reanudarAlojamiento(
    id: number
  ): Observable<AlojamientoRespuesta> {

    return this.http.post<AlojamientoRespuesta>(
      `${this.apiUrl}/${id}/reanudar`,
      {},
      this.opciones()
    );
  }

  // ====================================
  // 12. ELIMINAR FOTOGRAFÍA
  // DELETE /alojamientos/{id}/imagenes/{idImagen}
  // ====================================

  /**
   * DELETE /alojamientos/{id}/imagenes/{idImagen}. Elimina una imagen.
   *
   * @param idAlojamiento alojamiento
   * @param idImagen imagen
   * @returns las imágenes restantes
   */
  eliminarImagen(
    idAlojamiento: number,
    idImagen: number
  ): Observable<ImagenAlojamiento[]> {

    return this.http.delete<ImagenAlojamiento[]>(
      `${this.apiUrl}/${idAlojamiento}/imagenes/${idImagen}`,
      this.opciones()
    );
  }

  // ====================================
  // 13. MARCAR FOTO PRINCIPAL
  // PATCH /alojamientos/{id}/imagenes/{idImagen}/principal
  // ====================================

  /**
   * PATCH /alojamientos/{id}/imagenes/{idImagen}/principal. Marca la portada.
   *
   * @param idAlojamiento alojamiento
   * @param idImagen imagen
   * @returns las imágenes, la portada primero
   */
  marcarImagenPrincipal(
    idAlojamiento: number,
    idImagen: number
  ): Observable<ImagenAlojamiento[]> {

    return this.http.patch<ImagenAlojamiento[]>(
      `${this.apiUrl}/${idAlojamiento}/imagenes/${idImagen}/principal`,
      {},
      this.opciones()
    );
  }

  // ====================================
  // 14. AGREGAR FECHAS DE DISPONIBILIDAD
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
      periodo,
      this.opciones()
    );
  }

  // ====================================
  // 15. CONSULTAR DISPONIBILIDAD
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
      `${this.apiUrl}/${idAlojamiento}/disponibilidad`,
      this.opciones()
    );
  }

  // ====================================
  // 16. ACTUALIZAR PERÍODO
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
      periodo,
      this.opciones()
    );
  }

  // ====================================
  // 17. ELIMINAR PERÍODO
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
  ): Observable<{ message: string }> {

    return this.http.delete<{ message: string }>(
      `${this.apiUrl}/${idAlojamiento}/disponibilidad/${idPeriodo}`,
      this.opciones()
    );
  }
}
