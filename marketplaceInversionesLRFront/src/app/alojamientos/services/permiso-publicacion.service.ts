import { Injectable, inject } from '@angular/core';
import { Observable, map, of } from 'rxjs';

import { SessionService } from '../../core/services/session.service';
import { User } from '../../perfil/services/user';
import { AlojamientoRespuesta } from '../models/alojamiento';

// ======================================
// PERMISO PARA MODIFICAR UNA PUBLICACIÓN
// HU-16 (CA-101, CA-113, CA-114)
//
// Solo el anfitrión propietario o un ADMINISTRADOR pueden editar la
// publicación y sus servicios adicionales. Esta comprobación decide qué
// muestra la interfaz; el backend vuelve a validar cada operación y
// responde 403 a cualquier otro usuario.
// ======================================

@Injectable({
  providedIn: 'root'
})
export class PermisoPublicacionService {

  private readonly sesion = inject(SessionService);

  private readonly usuarios = inject(User);

  /**
   * Indica si el usuario autenticado puede modificar el alojamiento.
   *
   * - Administrador: siempre (CA-113).
   * - Anfitrión: solo si es el propietario. El token no trae el id del
   *   usuario, así que se compara `idAnfitrion` con el `idUsuario` de
   *   GET /usuarios/perfil.
   * - Cualquier otro rol o sin sesión: no (CA-101, CA-114).
   */
  puedeModificar(
    alojamiento: AlojamientoRespuesta
  ): Observable<boolean> {

    if (!this.sesion.autenticado()) {
      return of(false);
    }

    if (this.sesion.tieneRol('Administrador')) {
      return of(true);
    }

    if (!this.sesion.tieneRol('Anfitrión')) {
      return of(false);
    }

    return this.usuarios
      .obtenerMiPerfil()
      .pipe(
        map(perfil =>
          perfil.idUsuario !== undefined &&
          perfil.idUsuario !== null &&
          Number(perfil.idUsuario) === Number(alojamiento.idAnfitrion)
        )
      );
  }
}
