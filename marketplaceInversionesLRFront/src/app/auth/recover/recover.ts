
import {
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal
} from '@angular/core';

import {
  FormsModule,
  NgForm
} from '@angular/forms';

import {
  HttpErrorResponse
} from '@angular/common/http';

import {
  ActivatedRoute,
  RouterLink
} from '@angular/router';

import {
  takeUntilDestroyed
} from '@angular/core/rxjs-interop';

import {
  finalize,
  timeout
} from 'rxjs';

import { Auth } from '../services/auth';

// ======================================
// PASOS DE LA RECUPERACIÓN
// ======================================

type PasoRecuperacion =
  | 'solicitar'
  | 'restablecer'
  | 'finalizado';

// ======================================
// COMPONENTE RECUPERAR CONTRASEÑA
// ======================================


@Component({
  selector: 'app-recover',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink
  ],
  templateUrl: './recover.html'
})
export class Recover implements OnInit {

  // ======================================
  // SERVICIOS
  // ======================================

  private readonly auth = inject(Auth);

  private readonly route = inject(ActivatedRoute);

  private readonly destroyRef = inject(DestroyRef);

  // ======================================
  // ESTADOS
  // ======================================

  readonly paso =
    signal<PasoRecuperacion>('solicitar');

  readonly cargando = signal(false);

  readonly error = signal('');

  readonly mensaje = signal('');

  // ======================================
  // CAMPOS DE LOS FORMULARIOS
  // ======================================

  correo = '';

  token = '';

  contrasenia = '';

  confirmarContrasenia = '';

  mostrarContrasenia = false;

  // ======================================
  // INICIALIZAR
  // ======================================

  ngOnInit(): void {

    // Compatibilidad con una futura URL
    // /restablecer-contrasena?token=...

    if (
      this.route.snapshot.routeConfig?.path ===
      'restablecer-contrasena'
    ) {
      this.paso.set('restablecer');
    }

    this.route.queryParamMap
      .pipe(
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(params => {

        const recibido = params.get('token');

        if (recibido?.trim()) {

          this.token = recibido.trim();

          this.paso.set('restablecer');

        }

      });

  }

  // ======================================
  // VALIDAR NUEVA CONTRASEÑA
  // SEGÚN BACKEND (8)
  // ======================================

  get contraseniaValida(): boolean {

    const valor = this.contrasenia;

    return (
      valor.length >= 8 &&
      valor.length <= 64 &&

      // Límite de BCrypt del backend
      new TextEncoder().encode(valor).length <= 72 &&

      // Mayúscula
      /[A-ZÁÉÍÓÚÜÑ]/.test(valor) &&

      // Minúscula
      /[a-záéíóúüñ]/.test(valor) &&

      // Número
      /\d/.test(valor) &&

      // Símbolo
      /[^A-Za-z0-9]/.test(valor)
    );

  }

  // ======================================
  // COMPROBAR COINCIDENCIA
  // ======================================

  get coinciden(): boolean {

    return (
      this.contrasenia ===
      this.confirmarContrasenia
    );

  }

  // ======================================
  // 1. SOLICITAR RECUPERACIÓN
  // POST /auth/recuperar
  // ======================================

  solicitar(formulario: NgForm): void {

    if (
      this.cargando() ||
      formulario.invalid
    ) {
      return;
    }

    this.error.set('');

    this.mensaje.set('');

    this.cargando.set(true);

    this.auth.recuperarContrasena(
      this.correo.trim()
    )
      .pipe(

        timeout(20000),

        finalize(() => {
          this.cargando.set(false);
        }),

        takeUntilDestroyed(this.destroyRef)

      )
      .subscribe({

        next: () => {

          // Pasar al formulario del código

          this.paso.set('restablecer');

          // Mensaje genérico para proteger
          // la existencia de las cuentas

          this.mensaje.set(
            'Si el correo está registrado y el envío ' +
            'está disponible, recibirás un código de ' +
            'recuperación. Cópialo completo y pégalo aquí.'
          );

        },

        error: err => {

          this.error.set(
            this.mensajeError(
              err,
              'No fue posible solicitar la recuperación.'
            )
          );

        }

      });

  }

  // ======================================
  // 2. RESTABLECER CONTRASEÑA
  // POST /auth/restablecer
  // ======================================

  restablecer(formulario: NgForm): void {

    if (
      this.cargando() ||
      formulario.invalid
    ) {
      return;
    }

    // Validar código recibido

    if (!this.token.trim()) {

      this.error.set(
        'Pega el código completo que recibiste por correo.'
      );

      return;
    }

    // Validar contraseña nueva

    if (!this.contraseniaValida) {

      this.error.set(
        'La contraseña debe tener entre 8 y 64 ' +
        'caracteres, mayúscula, minúscula, ' +
        'número y símbolo.'
      );

      return;
    }

    // Confirmar coincidencia

    if (!this.coinciden) {

      this.error.set(
        'Las contraseñas no coinciden.'
      );

      return;
    }

    this.error.set('');

    this.mensaje.set('');

    this.cargando.set(true);

    // Enviar al backend el código y contraseña

    this.auth.restablecerContrasena(
      this.token.trim(),
      this.contrasenia
    )
      .pipe(

        timeout(20000),

        finalize(() => {
          this.cargando.set(false);
        }),

        takeUntilDestroyed(this.destroyRef)

      )
      .subscribe({

        next: () => {

          // Eliminar datos sensibles del formulario

          this.token = '';

          this.contrasenia = '';

          this.confirmarContrasenia = '';

          this.mostrarContrasenia = false;

          // Mostrar pantalla de éxito

          this.paso.set('finalizado');

          this.mensaje.set(
            'La contraseña se actualizó correctamente. ' +
            'Ya puedes iniciar sesión.'
          );

        },

        error: err => {

          this.error.set(
            this.mensajeError(
              err,
              'No fue posible cambiar la contraseña.'
            )
          );

        }

      });

  }

  // ======================================
  // 3. YA TENGO UN CÓDIGO
  // ======================================

  yaTengoCodigo(): void {

    this.paso.set('restablecer');

    this.error.set('');

    this.mensaje.set('');

  }

  // ======================================
  // 4. SOLICITAR UN CÓDIGO NUEVO
  // ======================================

  volverASolicitar(): void {

    this.paso.set('solicitar');

    this.error.set('');

    this.mensaje.set('');

    this.token = '';

    this.contrasenia = '';

    this.confirmarContrasenia = '';

  }

  // ======================================
  // 5. MANEJAR ERRORES
  // ======================================

  private mensajeError(
    err: unknown,
    defecto: string
  ): string {

    // Tiempo de espera

    if (
      err instanceof Error &&
      err.name === 'TimeoutError'
    ) {

      return 'El servidor tardó demasiado en responder.';

    }

    if (!(err instanceof HttpErrorResponse)) {
      return defecto;
    }

    // Error de conexión

    if (err.status === 0) {

      return (
        'No se pudo conectar con Spring Boot. ' +
        'Verifica el puerto 8081.'
      );

    }

    // Demasiadas solicitudes

    if (err.status === 429) {

      return (
        'Demasiadas solicitudes. Intenta más tarde.'
      );

    }

    // Token inválido, vencido o contraseña débil

    if (
      err.status === 400 &&
      this.paso() === 'restablecer'
    ) {

      const detalle =
        err.error?.message ??
        err.error?.mensaje;

      return (
        typeof detalle === 'string' &&
        detalle.trim()
      )
        ? detalle
        : 'El código es incorrecto, venció o ya fue usado; ' +
        'comprueba también la contraseña.';

    }

    return defecto;

  }

}
