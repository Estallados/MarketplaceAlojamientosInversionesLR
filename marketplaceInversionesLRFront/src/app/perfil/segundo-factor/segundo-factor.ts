import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, timeout } from 'rxjs';

import { SegundoFactorService } from '../services/segundo-factor.service';

/** Paso en el que está la tarjeta. */
type Paso = 'estado' | 'configurar' | 'desactivar';

/**
 * Tarjeta del perfil para habilitar o deshabilitar el segundo factor (HU-30, CA-174, CA-177, CA-178).
 *
 * El estado llega del perfil consultado al backend (`habilitado`); esta tarjeta no lo guarda ni lo
 * supone: después de cada cambio confirmado emite `cambiado` para que el perfil lo vuelva a leer.
 * El secreto y los códigos solo viven en memoria mientras dura el paso y se borran al terminar.
 */
@Component({
  selector: 'app-segundo-factor',
  standalone: true,
  templateUrl: './segundo-factor.html',
})
export class SegundoFactor {
  private readonly servicio = inject(SegundoFactorService);
  private readonly destroyRef = inject(DestroyRef);

  /** Estado leído de GET /usuarios/perfil; null si no se pudo consultar. */
  readonly habilitado = input<boolean | null>(null);

  /** Se emite cuando el servidor confirmó un cambio, para volver a consultar el perfil. */
  readonly cambiado = output<void>();

  readonly paso = signal<Paso>('estado');
  readonly procesando = signal(false);
  readonly error = signal('');
  readonly mensaje = signal('');

  readonly secreto = signal('');
  readonly qr = signal('');
  readonly codigo = signal('');
  readonly copiado = signal(false);

  /** Secreto en grupos de 4 para copiarlo a mano sin errores. */
  readonly secretoAgrupado = computed(() => this.secreto().replace(/(.{4})/g, '$1 ').trim());

  readonly codigoValido = computed(() => /^\d{6}$/.test(this.codigo()));

  /** Paso 1: pide el secreto al backend y dibuja el QR. */
  iniciarActivacion(): void {
    if (this.procesando() || this.habilitado() !== false) {
      return;
    }
    this.limpiarMensajes();
    this.procesando.set(true);
    this.servicio
      .activar()
      .pipe(
        timeout(15000),
        finalize(() => this.procesando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ secreto, uri }) => {
          if (!secreto || !uri) {
            this.error.set('El servidor no devolvió los datos para configurar la aplicación autenticadora.');
            return;
          }
          this.secreto.set(secreto);
          this.codigo.set('');
          this.paso.set('configurar');
          // La librería del QR se descarga solo cuando alguien configura el segundo factor.
          import('qrcode')
            .then((modulo) => (modulo.default ?? modulo).toDataURL(uri, { margin: 1, width: 200 }))
            .then((imagen) => this.qr.set(imagen))
            .catch(() => this.qr.set(''));
        },
        error: (err: unknown) => {
          this.error.set(this.mensajeError(err, 'No se pudo iniciar la activación del segundo factor.'));
          if (err instanceof HttpErrorResponse && err.status === 409) {
            // Ya estaba habilitado en el servidor: se vuelve a leer el estado real.
            this.cambiado.emit();
          }
        },
      });
  }

  /** Paso 2: confirma con el código de la app. Solo con el 200 del servidor se da por habilitado. */
  confirmarActivacion(): void {
    if (this.procesando() || this.paso() !== 'configurar') {
      return;
    }
    if (!this.codigoValido()) {
      this.error.set('Escribe el código de 6 dígitos que muestra tu aplicación autenticadora.');
      return;
    }
    this.limpiarMensajes();
    this.procesando.set(true);
    this.servicio
      .confirmar(this.codigo())
      .pipe(
        timeout(15000),
        finalize(() => this.procesando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.terminar(
            'Segundo factor habilitado. Desde ahora, al iniciar sesión con tu contraseña te pediremos el código que enviamos a tu correo.',
          );
        },
        error: (err: unknown) => {
          this.codigo.set('');
          this.error.set(this.mensajeError(err, 'No se pudo habilitar el segundo factor.'));
        },
      });
  }

  /** Muestra la confirmación de desactivación, que pide un código actual de la app. */
  iniciarDesactivacion(): void {
    if (this.procesando() || this.habilitado() !== true) {
      return;
    }
    this.limpiarMensajes();
    this.codigo.set('');
    this.paso.set('desactivar');
  }

  /** Deshabilita con el código de la app; el estado se actualiza al releer el perfil. */
  confirmarDesactivacion(): void {
    if (this.procesando() || this.paso() !== 'desactivar' || this.habilitado() !== true) {
      return;
    }
    if (!this.codigoValido()) {
      this.error.set('Escribe el código de 6 dígitos que muestra tu aplicación autenticadora.');
      return;
    }
    this.limpiarMensajes();
    this.procesando.set(true);
    this.servicio
      .desactivar(this.codigo())
      .pipe(
        timeout(15000),
        finalize(() => this.procesando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.terminar('Segundo factor deshabilitado. Tu inicio de sesión vuelve a pedir solo correo y contraseña.');
        },
        error: (err: unknown) => {
          this.codigo.set('');
          this.error.set(this.mensajeError(err, 'No se pudo deshabilitar el segundo factor.'));
          if (err instanceof HttpErrorResponse && err.status === 409) {
            this.cambiado.emit();
          }
        },
      });
  }

  /** Cancela el paso actual y borra el secreto y el código de la memoria. */
  cancelar(): void {
    if (this.procesando()) {
      return;
    }
    this.limpiarPaso();
    this.limpiarMensajes();
  }

  cambiarCodigo(valor: string): void {
    this.codigo.set((valor || '').replace(/\D/g, '').slice(0, 6));
    this.error.set('');
  }

  copiarSecreto(): void {
    const secreto = this.secreto();
    if (!secreto || !navigator.clipboard) {
      return;
    }
    navigator.clipboard
      .writeText(secreto)
      .then(() => this.copiado.set(true))
      .catch(() => this.copiado.set(false));
  }

  private terminar(texto: string): void {
    this.limpiarPaso();
    this.mensaje.set(texto);
    this.cambiado.emit();
  }

  private limpiarPaso(): void {
    this.paso.set('estado');
    this.secreto.set('');
    this.qr.set('');
    this.codigo.set('');
    this.copiado.set(false);
  }

  private limpiarMensajes(): void {
    this.error.set('');
    this.mensaje.set('');
  }

  private mensajeError(err: unknown, porDefecto: string): string {
    if (err instanceof Error && err.name === 'TimeoutError') {
      return 'El servidor tardó demasiado en responder.';
    }
    if (!(err instanceof HttpErrorResponse)) {
      return porDefecto;
    }
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor.';
    }
    if (err.status === 401) {
      return 'Tu sesión ha expirado. Inicia sesión nuevamente.';
    }
    const datos: unknown = err.error;
    if (datos && typeof datos === 'object') {
      const respuesta = datos as { message?: string; mensaje?: string };
      return respuesta.message || respuesta.mensaje || porDefecto;
    }
    return typeof datos === 'string' && datos ? datos : porDefecto;
  }
}
