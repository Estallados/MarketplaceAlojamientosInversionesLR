
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_URL } from '../core/services/api-url';
import { Observable } from 'rxjs';

// ======================================
// ROLES PERMITIDOS EN REGISTRO
// ======================================

/** Roles que se pueden elegir al registrarse. */
export type RegistroRol = 'HUESPED' | 'ANFITRION';

// ======================================
// 1. REGISTRO DE USUARIOS
// ======================================

/** Datos del registro (POST /auth/register). */
export interface RegistroRequest {
  /** Nombre completo. */
  nombre: string;
  /** Correo. */
  correo: string;
  /** Teléfono de contacto. */
  telefono: string;
  /** Rol con el que se crea la cuenta. */
  rol: RegistroRol;
  /** Contraseña. */
  contrasenia: string;
}

// ======================================
// RESPUESTA DEL REGISTRO
// ======================================

/** Respuesta del registro: la cuenta queda pendiente de verificar el correo. */
export interface RegistroResponse {
  /** Mensaje para el usuario. */
  message: string;
  /** Correo registrado. */
  correo: string;
  /** Indica que hay que confirmar el correo con el código. */
  requiereVerificacion: boolean;
}

// ======================================
// 2. VERIFICACIÓN DE CORREO
// ======================================

/** Respuesta de la verificación del correo, con la sesión iniciada. */
export interface VerificacionResponse {
  /** Mensaje para el usuario. */
  message: string;
  /** Token JWT. */
  token: string;
  /** Rol del usuario. */
  rol: string;
  /** Correo del usuario. */
  correo: string;
}

// ======================================
// 3. REENVÍO DE CÓDIGO
// ======================================

/** Respuesta del reenvío del código de verificación. */
export interface ReenvioCodigoResponse {
  /** Mensaje para el usuario. */
  message: string;
}

// ======================================
// 4. INICIO DE SESIÓN
// ======================================

/** Datos del inicio de sesión. */
export interface LoginRequest {
  /** Correo. */
  correo: string;
  /** Contraseña. */
  contrasenia: string;
  /** Código del segundo factor; solo si la cuenta lo tiene activo. */
  codigo?: string;
}

// ======================================
// RESPUESTA DEL LOGIN
// ======================================

/** Respuesta del inicio de sesión. */
export interface LoginResponse {
  /** Token JWT. */
  token: string;
  /** Rol del usuario. */
  rol: string;
  /** Correo del usuario. */
  correo: string;
  /** Mensaje para el usuario. */
  message?: string;
  /** Indica si la cuenta se acaba de crear (inicio con Google). */
  cuentaNueva?: boolean;
}

// ======================================
// 5. RECUPERACIÓN DE CONTRASEÑA
// ======================================

/** Datos para pedir la recuperación de la contraseña. */
export interface RecuperarContrasenaRequest {
  /** Correo de la cuenta. */
  correo: string;
}

/** Respuesta de la solicitud de recuperación. */
export interface RecuperarContrasenaResponse {
  /** Mensaje para el usuario. */
  message: string;
}

// ======================================
// 6. RESTABLECER CONTRASEÑA
// ======================================

/** Datos para cambiar la contraseña con el token de recuperación. */
export interface RestablecerContrasenaRequest {
  /** Token que llegó por correo. */
  token: string;
  /** Contraseña nueva. */
  contrasenia: string;
}

/** Respuesta del cambio de contraseña. */
export interface RestablecerContrasenaResponse {
  /** Mensaje para el usuario. */
  message: string;
}

// ======================================
// 7. CONFIGURACIÓN DE GOOGLE
// ======================================

/** Configuración del botón de Google. */
export interface GoogleConfiguracionResponse {
  /** Indica si el inicio con Google está configurado. */
  habilitado: boolean;
  /** Client ID de Google; null si no está configurado. */
  clientId: string | null;
}

// ======================================
// 8. LOGIN CON GOOGLE
// ======================================

/** Datos del inicio de sesión con Google. */
export interface GoogleLoginRequest {
  /** ID token que entregó el botón de Google. */
  idToken: string;
  /** Rol con el que se crea la cuenta si es nueva. */
  rol: RegistroRol;
  /** Código del segundo factor; solo si la cuenta lo tiene activo. */
  codigo?: string;
}

// ======================================
// RESPUESTA DEL LOGIN CON GOOGLE
// ======================================

/** Respuesta del inicio con Google: igual que la del login, más si la cuenta es nueva. */
export interface GoogleLoginResponse
  extends LoginResponse {
  cuentaNueva?: boolean;
}

// ======================================
// SERVICIO DE AUTENTICACIÓN
// ======================================

/**
 * Llamadas a las rutas públicas de autenticación del backend ({@code /auth}): registro,
 * verificación del correo, login, recuperación de contraseña e inicio con Google.
 */
@Injectable({
  providedIn: 'root'
})
export class Auth {

  // ======================================
  // INYECCIÓN DE DEPENDENCIAS
  // ======================================

  /** Cliente HTTP. */
  private readonly http = inject(HttpClient);

  private readonly baseUrl = inject(API_URL);

  // URL BASE:
  // http://localhost:8081/auth

  /** Ruta base de la autenticación. */
  private readonly apiUrl =
    `${this.baseUrl.replace(/\/+$/, '')}/auth`;

  // ======================================
  // 1. REGISTRAR USUARIO
  // POST /auth/register
  // ======================================
/**
 * POST /auth/register. Crea la cuenta y el backend envía el código de verificación al correo.
 *
 * @param datos datos del registro
 * @returns la respuesta del registro
 */
registrar(
  datos: RegistroRequest
): Observable<RegistroResponse> {
  return this.http.post<RegistroResponse>(
    `${this.apiUrl}/register`,
    {
      nombre: datos.nombre.trim(),
      correo: datos.correo.trim().toLowerCase(),
      telefono: datos.telefono?.trim() ?? '',
      rol: datos.rol,
      contrasenia: datos.contrasenia
    }
  );
}

  // ======================================
  // 2. VERIFICAR CORREO
  // POST /auth/verificar
  // ======================================

  /**
   * POST /auth/verificar. Confirma el correo con el código e inicia la sesión.
   *
   * @param correo correo de la cuenta
   * @param codigo código de 6 dígitos
   * @returns la sesión iniciada
   */
  verificarCorreo(
    correo: string,
    codigo: string
  ): Observable<VerificacionResponse> {

    return this.http.post<VerificacionResponse>(
      `${this.apiUrl}/verificar`,
      {
        correo: correo.trim().toLowerCase(),
        codigo: codigo.trim()
      }
    );

  }

  // ======================================
  // 3. REENVIAR CÓDIGO DE VERIFICACIÓN
  // POST /auth/reenviarcodigo
  // ======================================


  /**
   * POST /auth/reenviarcodigo. Pide un código de verificación nuevo.
   *
   * @param correo correo de la cuenta
   * @returns la respuesta con el mensaje
   */
 

  reenviarCodigo(
    correo: string
  ): Observable<ReenvioCodigoResponse> {


    return this.http.post<ReenvioCodigoResponse>(
      `${this.apiUrl}/reenviarcodigo`,
      {
        correo: correo.trim().toLowerCase()
      }
    );

  }

  // ======================================
  // 4. INICIAR SESIÓN NORMAL
  // POST /auth/login
  // ======================================


  /**
   * POST /auth/login. Inicia sesión con correo y contraseña (y el código del segundo factor, si
   * aplica).
   *
   * @param datos credenciales
   * @returns la sesión iniciada
   */


  login(
    datos: LoginRequest
  ): Observable<LoginResponse> {

    const solicitud: LoginRequest = {

      correo: datos.correo.trim().toLowerCase(),

      contrasenia: datos.contrasenia,

      ...(datos.codigo?.trim()
        ? {
          codigo: datos.codigo.trim()
        }
        : {})

    };
    return this.http.post<LoginResponse>(
      `${this.apiUrl}/login`,
      solicitud
    );

  }

  // ======================================
  // 5. SOLICITAR RECUPERACIÓN
  // POST /auth/recuperar
  // ======================================


  /**
   * POST /auth/recuperar. Pide el correo con el código para recuperar la contraseña.
   *
   * @param correo correo de la cuenta
   * @returns la respuesta con el mensaje
   */
  recuperarContrasena(
    correo: string
  ): Observable<RecuperarContrasenaResponse> {

    const datos: RecuperarContrasenaRequest = {

      correo: correo.trim().toLowerCase()

    };

    return this.http.post<RecuperarContrasenaResponse>(
      `${this.apiUrl}/recuperar`,
      datos
    );

  }

  // ======================================
  // 6. RESTABLECER CONTRASEÑA
  // POST /auth/restablecer
  // ======================================

  /**
   * POST /auth/restablecer. Cambia la contraseña con el código de recuperación.
   *
   * @param token código que llegó por correo
   * @param contrasenia contraseña nueva
   * @returns la respuesta con el mensaje
   */
  restablecerContrasena(
    token: string,
    contrasenia: string
  ): Observable<RestablecerContrasenaResponse> {

    const datos: RestablecerContrasenaRequest = {

      token: token.trim(),

      contrasenia: contrasenia

    };

    return this.http.post<RestablecerContrasenaResponse>(
      `${this.apiUrl}/restablecer`,
      datos
    );

  }

  // ======================================
  // 7. CONSULTAR CONFIGURACIÓN GOOGLE
  // GET /auth/google/configuracion
  // ======================================

  /**
   * GET /auth/google/configuracion. Dice si el inicio con Google está activo y con qué client ID.
   *
   * @returns la configuración del botón
   */

  configuracionGoogle():
    Observable<GoogleConfiguracionResponse> {


    return this.http.get<GoogleConfiguracionResponse>(
      `${this.apiUrl}/google/configuracion`
    );

  }

  // ======================================
  // 8. INICIAR SESIÓN CON GOOGLE
  // POST /auth/google
  // ======================================

  /**
   * POST /auth/google. Inicia sesión (o crea la cuenta) con el ID token de Google.
   *
   * @param idToken ID token del botón de Google
   * @param rol rol de la cuenta si es nueva
   * @param codigo código del segundo factor, si la cuenta lo tiene activo
   * @returns la sesión iniciada
   */
  iniciarConGoogle(
    idToken: string,
    rol: RegistroRol = 'HUESPED',
    codigo?: string
  ): Observable<GoogleLoginResponse> {

    const datos: GoogleLoginRequest = {

      idToken: idToken,

      rol: rol,

      ...(codigo?.trim()
        ? {
          codigo: codigo.trim()
        }
        : {})

    };

    return this.http.post<GoogleLoginResponse>(
      `${this.apiUrl}/google`,
      datos
    );

  }

}
