
import {
  Injectable,
  computed,
  signal
} from '@angular/core';

// ======================================
// ROLES DEL SISTEMA
// ======================================

/** Roles como se muestran en el frontend. */
export type RolUsuario =
  | 'Huésped'
  | 'Anfitrión'
  | 'Administrador';

// ======================================
// DATOS DE LA SESIÓN
// ======================================

/** Datos de la sesión que devuelve el backend al iniciar sesión. */
export interface DatosSesion {
  /** Token JWT. */
  token: string;
  /** Rol tal como lo envía el backend (por ejemplo HUESPED). */
  rol: string;
  /** Correo del usuario. */
  correo: string;
}

/**
 * Sesión del usuario. Guarda el token, el rol y el correo en sessionStorage, los expone como
 * señales y revisa que el token no haya vencido; si venció, cierra la sesión.
 */
@Injectable({
  providedIn: 'root'
})
export class SessionService {

  // ======================================
  // ESTADO DE LA SESIÓN
  // ======================================

  /** Datos de la sesión actual; null si no hay sesión. */
  private readonly sesion = signal<DatosSesion | null>(
    this.recuperarSesion()
  );

  /** Datos de la sesión, de solo lectura. */
  readonly usuario = this.sesion.asReadonly();

  // ======================================
  // VERIFICAR AUTENTICACIÓN
  // ======================================

  /** Indica si hay una sesión con un token vigente. */
  readonly autenticado = computed(() => {

    const datos = this.sesion();

    return (
      datos !== null &&
      this.tokenVigente(datos.token)
    );
  });

  // ======================================
  // CORREO DEL USUARIO
  // ======================================

  readonly correo = computed(() => {

    return this.sesion()?.correo ?? '';
  });

  // ======================================
  // ROL DEL USUARIO
  // ======================================

  readonly rol = computed<RolUsuario | null>(() => {

    const datos = this.sesion();

    if (!datos || !this.tokenVigente(datos.token)) {
      return null;
    }

    return this.normalizarRol(datos.rol);
  });

  // ======================================
  // 1. GUARDAR SESIÓN
  // ======================================

  /**
   * Guarda la sesión después del login: valida que vengan el token, el rol y el correo, y que el
   * token no haya vencido.
   *
   * @param datos token, rol y correo que devolvió el backend
   * @throws Error si faltan datos o el token no es válido
   */
  iniciarSesion(datos: DatosSesion): void {

    if (
      !datos.token ||
      !datos.rol ||
      !datos.correo
    ) {
      throw new Error(
        'La respuesta de autenticación está incompleta.'
      );
    }

    if (!this.tokenVigente(datos.token)) {
      throw new Error(
        'El token JWT es inválido o ha expirado.'
      );
    }

    sessionStorage.setItem(
      'token',
      datos.token
    );

    sessionStorage.setItem(
      'rol',
      datos.rol
    );

    sessionStorage.setItem(
      'correo',
      datos.correo
    );

    this.sesion.set({ ...datos });
  }

  // ======================================
  // 2. OBTENER TOKEN JWT
  // ======================================

  /**
   * Token JWT de la sesión, si sigue vigente; si venció, cierra la sesión.
   *
   * @returns el token, o null si no hay sesión válida
   */
  obtenerToken(): string | null {

    const datos = this.sesion();

    if (!datos) {
      return null;
    }

    if (!this.tokenVigente(datos.token)) {
      return null;
    }

    return datos.token;
  }

  // ======================================
  // 3. VERIFICAR PERMISOS
  // ======================================

  /**
   * Indica si el usuario tiene alguno de los roles indicados.
   *
   * @param roles roles permitidos
   * @returns true si su rol está entre ellos
   */
  tieneRol(...roles: RolUsuario[]): boolean {

    const rolActual = this.rol();

    return (
      rolActual !== null &&
      roles.includes(rolActual)
    );
  }

  // ======================================
  // 4. CERRAR SESIÓN
  // ======================================

  /** Borra la sesión de sessionStorage y de la señal. */
  cerrarSesion(): void {

    sessionStorage.removeItem('token');
    sessionStorage.removeItem('rol');
    sessionStorage.removeItem('correo');

    this.sesion.set(null);
  }

  // ======================================
  // 5. RECUPERAR SESIÓN GUARDADA
  // ======================================

  /**
   * Recupera la sesión guardada al recargar la página, si el token sigue vigente.
   *
   * @returns los datos de la sesión, o null
   */
  private recuperarSesion(): DatosSesion | null {

    const token =
      sessionStorage.getItem('token');

    const rol =
      sessionStorage.getItem('rol');

    const correo =
      sessionStorage.getItem('correo');

    if (!token || !rol || !correo) {
      return null;
    }

    if (!this.tokenVigente(token)) {

      sessionStorage.removeItem('token');
      sessionStorage.removeItem('rol');
      sessionStorage.removeItem('correo');

      return null;
    }

    return {
      token,
      rol,
      correo
    };
  }

  // ======================================
  // 6. NORMALIZAR ROL
  // ======================================

  /**
   * Convierte el rol del backend (HUESPED, ROLE_ANFITRION, etc.) en el rol del frontend. Ignora
   * mayúsculas, tildes y el prefijo ROLE_.
   *
   * @param valor rol como llega del backend
   * @returns el rol del frontend, o null si no lo reconoce
   */
  private normalizarRol(
    valor: string
  ): RolUsuario | null {

    const rol = valor
      .trim()
      .toUpperCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/^ROLE_/, '');

    switch (rol) {

      case 'HUESPED':
        return 'Huésped';

      case 'ANFITRION':
        return 'Anfitrión';

      case 'ADMIN':
      case 'ADMINISTRADOR':
        return 'Administrador';

      default:
        return null;
    }
  }

  // ======================================
  // 7. COMPROBAR EXPIRACIÓN DEL JWT
  // ======================================

  /**
   * Revisa la fecha de vencimiento (exp) del JWT sin llamar al backend.
   *
   * @param token token JWT
   * @returns true si tiene formato válido y no ha vencido
   */
  private tokenVigente(token: string): boolean {

    try {

      const partes = token.split('.');

      if (partes.length !== 3) {
        return false;
      }

      const base64 = partes[1]
        .replace(/-/g, '+')
        .replace(/_/g, '/');

      const payload = JSON.parse(
        atob(base64)
      ) as {
        exp?: number;
      };

      if (typeof payload.exp !== 'number') {
        return false;
      }

      return payload.exp * 1000 > Date.now();

    } catch {

      return false;
    }
  }
}
