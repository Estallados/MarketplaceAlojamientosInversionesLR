import {
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal
} from '@angular/core';

import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';

import { AlojamientoRespuesta } from '../models/alojamiento';
import {
  AccionEstado,
  accionesPermitidas,
  aceptaNuevasReservas,
  esEstadoAlojamiento,
  presentacionEstado
} from '../models/estado-publicacion';

import { PublicacionHostService } from '../services/publicacion-host.service';
import { SessionService } from '../../core/services/session.service';
import { datosFaltantesParaRevision } from '../validaciones/validaciones-alojamiento';

/** Mensaje de éxito de cada transición. */
const EXITO: Record<AccionEstado, string> = {
  enviarRevision:
    'La publicación se envió a revisión. Quedará activa cuando ' +
    'un administrador la apruebe.',
  pausar:
    'La publicación quedó pausada. No recibirá nuevas reservas; ' +
    'las reservas ya confirmadas se mantienen.',
  reanudar:
    'La publicación se reanudó.'
};

// ======================================
// GESTIÓN DEL ESTADO DE LA PUBLICACIÓN
// HU-17: enviar a revisión (CA-157), pausar (CA-108), reanudar,
// estados permitidos (CA-104) y disponibilidad para reservas (CA-159).
//
// El estado que se muestra es siempre el que devuelve el servidor: no hay
// cambios optimistas. Las transiciones las pide el ANFITRION (el backend
// responde 403 a otros roles); la aprobación es del administrador (HU-32).
// ======================================

@Component({
  selector: 'app-gestion-estado',
  standalone: true,
  templateUrl: './gestion-estado.html'
})
export class GestionEstado {

  private readonly publicaciones =
    inject(PublicacionHostService);

  private readonly sesion =
    inject(SessionService);

  private readonly destroyRef =
    inject(DestroyRef);

  // ======================================
  // ENTRADAS Y SALIDAS
  // ======================================

  /** Publicación tal como la devolvió el servidor. */
  readonly alojamiento = input.required<AlojamientoRespuesta>();

  /** El formulario de edición tiene cambios que no se han guardado. */
  readonly cambiosSinGuardar = input(false);

  /** Publicación actualizada que devolvió el servidor tras una transición. */
  readonly estadoCambiado = output<AlojamientoRespuesta>();

  /** El backend respondió 403 o 401. */
  readonly accesoRevocado = output<'denegado' | 'sin-sesion'>();

  // ======================================
  // ESTADO DE LA VISTA
  // ======================================

  /** Las transiciones del contrato exigen el rol ANFITRION. */
  readonly esAnfitrion =
    this.sesion.tieneRol('Anfitrión');

  readonly procesando =
    signal<AccionEstado | null>(null);

  readonly confirmandoPausa =
    signal(false);

  readonly mensaje =
    signal('');

  readonly error =
    signal('');

  /** Datos que faltan para enviar a revisión. */
  readonly faltantes =
    signal<string[]>([]);

  readonly estado = computed(
    () => this.alojamiento().estado
  );

  readonly presentacion = computed(
    () => presentacionEstado(this.estado())
  );

  readonly estadoReconocido = computed(
    () => esEstadoAlojamiento(this.estado())
  );

  /** Solo una publicación ACTIVA se muestra disponible (CA-159). */
  readonly disponibleParaReservas = computed(
    () => aceptaNuevasReservas(this.estado())
  );

  /** Acciones que el estado actual permite y el rol puede pedir. */
  readonly acciones = computed<AccionEstado[]>(
    () => this.esAnfitrion ? accionesPermitidas(this.estado()) : []
  );

  puede(accion: AccionEstado): boolean {
    return this.acciones().includes(accion);
  }

  // ======================================
  // 1. ENVIAR A REVISIÓN (CA-157)
  // POST /alojamientos/{id}/enviarrevision
  // ======================================

  enviarARevision(): void {

    if (!this.preparar('enviarRevision')) {
      return;
    }

    // El backend revisa los datos guardados, no los del formulario.

    if (this.cambiosSinGuardar()) {

      this.error.set(
        'Guarda los cambios de la publicación antes de enviarla a revisión.'
      );

      return;
    }

    const a = this.alojamiento();

    const faltantes = datosFaltantesParaRevision({
      ...a,
      cantidadImagenes:
        a.detalleImagenes?.length ??
        a.imagenes?.length ??
        0
    }).map(f => f.nombre);

    if (faltantes.length > 0) {

      this.faltantes.set(faltantes);

      this.error.set(
        'Completa los datos antes de enviar a revisión. Falta: ' +
        faltantes.join(', ') +
        '.'
      );

      return;
    }

    this.ejecutar(
      'enviarRevision',
      this.publicaciones.enviarRevision(a.idAlojamiento)
    );
  }

  // ======================================
  // 2. PAUSAR (CA-108)
  // POST /alojamientos/{id}/pausar
  // ======================================

  /** Pide confirmación antes de pausar. */
  solicitarPausa(): void {

    if (!this.preparar('pausar')) {
      return;
    }

    this.confirmandoPausa.set(true);
  }

  cancelarPausa(): void {
    this.confirmandoPausa.set(false);
  }

  confirmarPausa(): void {

    if (!this.preparar('pausar')) {
      return;
    }

    this.ejecutar(
      'pausar',
      this.publicaciones.pausar(this.alojamiento().idAlojamiento)
    );
  }

  // ======================================
  // 3. REANUDAR
  // POST /alojamientos/{id}/reanudar
  // ======================================

  reanudar(): void {

    if (!this.preparar('reanudar')) {
      return;
    }

    this.ejecutar(
      'reanudar',
      this.publicaciones.reanudar(this.alojamiento().idAlojamiento)
    );
  }

  // ======================================
  // 4. EJECUCIÓN COMÚN
  // ======================================

  /**
   * Limpia los mensajes y comprueba que no haya otra petición en curso
   * (evita el doble envío) y que el estado actual permita la acción.
   */
  private preparar(accion: AccionEstado): boolean {

    if (this.procesando() !== null) {
      return false;
    }

    this.mensaje.set('');
    this.error.set('');
    this.faltantes.set([]);

    if (!this.puede(accion)) {

      this.error.set(
        'La acción no está disponible para una publicación en estado ' +
        `"${this.presentacion().etiqueta}".`
      );

      return false;
    }

    return true;
  }

  private ejecutar(
    accion: AccionEstado,
    peticion: Observable<AlojamientoRespuesta>
  ): void {

    this.procesando.set(accion);

    peticion
      .pipe(
        finalize(() => {
          this.procesando.set(null);
          this.confirmandoPausa.set(false);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({

        next: actualizado => {

          // El estado nuevo es el que devolvió el servidor.

          this.estadoCambiado.emit(actualizado);

          this.mensaje.set(
            accion === 'reanudar'
              ? `${EXITO.reanudar} Estado actual: ` +
                `${presentacionEstado(actualizado.estado).etiqueta}.`
              : EXITO[accion]
          );
        },

        error: err => {
          this.manejarError(err);
        }
      });
  }

  private manejarError(err: unknown): void {

    if (err instanceof HttpErrorResponse) {

      if (err.status === 403) {
        this.accesoRevocado.emit('denegado');
        return;
      }

      if (err.status === 401) {
        this.accesoRevocado.emit('sin-sesion');
        return;
      }

      // 409: faltan datos o el estado cambió en el servidor. Se vuelve a
      // consultar la publicación para no mostrar un estado desactualizado.

      if (err.status === 409) {
        this.error.set(this.mensajeError(err));
        this.recargar();
        return;
      }

      if (err.status === 404) {
        this.error.set(
          'La publicación no existe o ya no está disponible.'
        );
        return;
      }
    }

    this.error.set(this.mensajeError(err));
  }

  /** GET /alojamientos/{id} para sincronizar el estado tras un 409. */
  private recargar(): void {

    this.publicaciones
      .obtenerAlojamiento(this.alojamiento().idAlojamiento)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: actual => this.estadoCambiado.emit(actual),
        // Si falla, se conserva el último estado conocido y el error del 409.
        error: () => undefined
      });
  }

  private mensajeError(err: unknown): string {

    if (err instanceof HttpErrorResponse) {

      if (err.status === 0) {
        return (
          'No se pudo conectar con el servidor. ' +
          'Revisa la conexión e inténtalo nuevamente.'
        );
      }

      if (
        err.error &&
        typeof err.error.message === 'string'
      ) {
        return err.error.message;
      }

      return (
        'No se pudo cambiar el estado de la publicación ' +
        `(HTTP ${err.status}).`
      );
    }

    return (
      'No se pudo cambiar el estado de la publicación. ' +
      'Inténtalo nuevamente.'
    );
  }
}
