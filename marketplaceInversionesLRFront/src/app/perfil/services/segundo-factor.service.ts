import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/services/api-url';

/** Secreto pendiente de confirmar que devuelve POST /usuarios/segundofactor/activar. */
export interface ActivacionSegundoFactor {
  /** Secreto en base32 para escribirlo a mano en la app autenticadora. */
  secreto: string;
  /** URI otpauth:// que se muestra como código QR. */
  uri: string;
}

/** Respuesta con mensaje de confirmar y desactivar. */
export interface MensajeSegundoFactor {
  message?: string;
}

/**
 * Segundo factor de la cuenta propia (HU-30, RNF-023): /usuarios/segundofactor.
 *
 * Contrato del backend: activar y desactivar se confirman con el código de 6 dígitos de una app
 * autenticadora (TOTP). Con el segundo factor habilitado, el inicio de sesión con contraseña pide
 * además el código que se envía al correo (POST /auth/login). El estado se lee de
 * GET /usuarios/perfil (`totpHabilitado`). El token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class SegundoFactorService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/usuarios/segundofactor`;

  /** Paso 1: genera el secreto (queda pendiente hasta confirmarlo). 409 si ya está habilitado. */
  activar(): Observable<ActivacionSegundoFactor> {
    return this.http.post<ActivacionSegundoFactor>(`${this.base}/activar`, {});
  }

  /** Paso 2: confirma con el código actual de la app; desde ahí el login pide el código del correo. */
  confirmar(codigo: string): Observable<MensajeSegundoFactor> {
    return this.http.post<MensajeSegundoFactor>(`${this.base}/confirmar`, { codigo: codigo.trim() });
  }

  /** Deshabilita el segundo factor con un código actual de la app. 409 si no estaba habilitado. */
  desactivar(codigo: string): Observable<MensajeSegundoFactor> {
    return this.http.post<MensajeSegundoFactor>(`${this.base}/desactivar`, { codigo: codigo.trim() });
  }
}
