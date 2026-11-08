
import { Injectable, inject } from '@angular/core';
import {
  HttpClient,
  HttpHeaders
} from '@angular/common/http';
import { Observable } from 'rxjs';

// Datos del usuario devueltos por Spring Boot
/**
 * Datos del perfil del usuario, tal como los devuelve el backend. Algunos campos tienen nombres
 * alternativos porque se aceptan varias formas de respuesta.
 */
export interface UsuarioDTO {
  /** Identificador del usuario. */
  idUsuario?: number;
  /** Nombre. */
  nombre?: string;
  /** Apellido, si el backend lo envía aparte. */
  apellido?: string;
  /** Correo. */
  correo?: string;
  /** Teléfono de contacto. */
  telefono?: string;
  /** Fecha en que se creó la cuenta. */
  fechaRegistro?: string;
  /** Nombre alternativo de la fecha de registro. */
  fechaCreacion?: string;
  /** Estado de la cuenta. */
  estado?: string;
  /** Nombre alternativo del estado. */
  estadoUsuario?: string;
  /** Indica si la cuenta está activa (forma alternativa). */
  activo?: boolean;
  /** Rol del usuario. */
  rol?: string;
}

// Respuestas de actualización
/** Respuesta del backend con un mensaje y, a veces, el nuevo estado de la cuenta. */
export interface MensajeRespuesta {
  /** Mensaje (nombre en inglés que usa el backend). */
  message?: string;
  /** Mensaje (nombre alternativo). */
  mensaje?: string;
  /** Nuevo estado de la cuenta, si cambió. */
  estado?: string;
}

/**
 * Perfil del usuario autenticado: consultarlo, cambiar el nombre y el teléfono, y desactivar la
 * cuenta.
 */
@Injectable({
  providedIn: 'root'
})
export class User {

  /** Cliente HTTP. */
  private readonly http = inject(HttpClient);

  /** Ruta base de los usuarios en el backend. */
  private readonly apiUrl =
    'http://localhost:8081/usuarios';

  // Obtener token JWT

  /**
   * Arma la cabecera Authorization con el token guardado en la sesión.
   *
   * @returns cabeceras con el token
   */
  private obtenerHeaders(): HttpHeaders {

    const token =
      sessionStorage.getItem('token') ?? '';

    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  // =====================================
  // CONSULTAR MI PERFIL
  // GET /usuarios/perfil
  // =====================================

  /**
   * GET /usuarios/perfil. Consulta el perfil del usuario autenticado.
   *
   * @returns el perfil
   */
  obtenerMiPerfil(): Observable<UsuarioDTO> {

    return this.http.get<UsuarioDTO>(
      `${this.apiUrl}/perfil`,
      {
        headers: this.obtenerHeaders()
      }
    );
  }

  // =====================================
  // ACTUALIZAR MI PERFIL
  // PUT /usuarios/perfil
  // =====================================

  /**
   * PUT /usuarios/perfil. Cambia el nombre y el teléfono; el backend ignora los demás campos.
   *
   * @param nombre nombre nuevo
   * @param telefono teléfono nuevo
   * @returns la respuesta con el mensaje
   */
  actualizarMiPerfil(
    nombre: string,
    telefono: string
  ): Observable<MensajeRespuesta> {

    return this.http.put<MensajeRespuesta>(
      `${this.apiUrl}/perfil`,
      {
        nombre,
        telefono
      },
      {
        headers: this.obtenerHeaders()
      }
    );
  }

  // =====================================
  // DESACTIVAR MI CUENTA
  // PATCH /usuarios/perfil/desactivar
  // =====================================

  /**
   * PATCH /usuarios/perfil/desactivar. Desactiva la cuenta propia; se puede reactivar desde el
   * login.
   *
   * @returns la respuesta con el mensaje y el nuevo estado
   */
  desactivarMiCuenta(): Observable<MensajeRespuesta> {

    return this.http.patch<MensajeRespuesta>(
      `${this.apiUrl}/perfil/desactivar`,
      {},
      {
        headers: this.obtenerHeaders()
      }
    );
  }
}
