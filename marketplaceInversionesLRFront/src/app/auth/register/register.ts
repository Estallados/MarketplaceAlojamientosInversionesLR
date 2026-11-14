
import {
  Component,
  ChangeDetectorRef,
  inject
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
  finalize,
  timeout
} from 'rxjs';

import {
  Auth,
  RegistroRequest,
  RegistroRol
} from '../services/auth';

// ======================================
// COMPONENTE DE REGISTRO
// ======================================

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink
  ],
  templateUrl: './register.html'
})
export class Register {

  // ======================================
  // SERVICIOS
  // ======================================

  private readonly authService = inject(Auth);

  private readonly cdr = inject(ChangeDetectorRef);

  private readonly route = inject(ActivatedRoute);

  // ======================================
  // DATOS DEL FORMULARIO
  // ======================================

  nombre = '';

  correo = '';

  telefono = '';

  contrasena = '';

  // Rol inicial: anfitrión si se llega con ?rol=anfitrion (botón "Publicar alojamiento" del inicio)
  rol: RegistroRol =
    this.route.snapshot.queryParamMap.get('rol') === 'anfitrion' ? 'ANFITRION' : 'HUESPED';

  // ======================================
  // MOSTRAR / OCULTAR CONTRASEÑA
  // ======================================

  showPassword = false;

  // ======================================
  // ESTADOS DE LA PÁGINA
  // ======================================

  guardando = false;

  cuentaCreada = false;

  mensaje = '';

  error = '';

  // Máximo 25 segundos esperando respuesta.
  private readonly tiempoEspera = 25000;

  // ======================================
  // 1. VALIDAR CONTRASEÑA
  // ======================================

  private contrasenaValida(): boolean {

    const expresion =
      /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

    return expresion.test(this.contrasena);
  }

  // ======================================
  // 2. REGISTRAR USUARIO
  // ======================================

  registrar(form: NgForm): void {

    // Evitar solicitudes duplicadas.
    if (
      this.guardando ||
      this.cuentaCreada
    ) {
      return;
    }

    // Limpiar mensajes.
    this.mensaje = '';
    this.error = '';

    // ====================================
    // VALIDAR FORMULARIO
    // ====================================

    if (
      form.invalid ||
      !this.nombre.trim() ||
      !this.correo.trim()
    ) {

      this.error =
        'Completa correctamente todos los campos obligatorios.';

      return;
    }

    // ====================================
    // VALIDAR CONTRASEÑA
    // ====================================

    if (!this.contrasenaValida()) {

      this.error =
        'La contraseña debe tener al menos ' +
        '8 caracteres, una mayúscula, ' +
        'una minúscula, un número y un símbolo.';

      return;
    }

    // ====================================
    // VALIDAR TELÉFONO
    // ====================================

    const telefonoLimpio =
      this.telefono.trim();

    const telefonoNormalizado =
      telefonoLimpio.replace(
        /[\s().-]/g,
        ''
      );

    if (
      telefonoLimpio &&
      !/^\+?\d{7,15}$/.test(
        telefonoNormalizado
      )
    ) {

      this.error =
        'El teléfono debe tener entre 7 y 15 dígitos.';

      return;
    }

    // ====================================
    // PREPARAR DATOS PARA SPRING BOOT
    // ====================================

    const datos: RegistroRequest = {

      nombre: this.nombre.trim(),

      correo: this.correo
        .trim()
        .toLowerCase(),

      telefono: telefonoLimpio,

      rol: this.rol,

      contrasenia: this.contrasena

    };

    // ====================================
    // ACTIVAR ESTADO DE CARGA
    // ====================================

    this.guardando = true;

    this.cdr.markForCheck();

    console.log(
      'Iniciando registro de usuario...'
    );

    // ====================================
    // ENVIAR REGISTRO AL BACKEND
    // ====================================

    this.authService.registrar(datos)
      .pipe(

        // Evita cargar indefinidamente.
        timeout({
          first: this.tiempoEspera
        }),

        // Siempre restablecer el botón,
        // tanto en éxito como en error.
        finalize(() => {

          this.guardando = false;

          this.cdr.markForCheck();

          console.log(
            'Petición de registro finalizada.'
          );

        })

      )
      .subscribe({

        // ==================================
        // REGISTRO EXITOSO
        // ==================================

        next: (respuesta) => {

          console.log(
            'Registro confirmado por Spring Boot.'
          );

          // Detener carga.
          this.guardando = false;

          // Marcar cuenta creada.
          this.cuentaCreada = true;

          // Conservar el correo.
          this.correo =
            respuesta?.correo || datos.correo;

          // Limpiar contraseña por seguridad.
          this.contrasena = '';

          // Eliminar errores.
          this.error = '';

          // Mostrar mensaje de confirmación.
          this.mensaje =
            '¡Cuenta creada correctamente! ' +
            'Te hemos enviado un código de verificación ' +
            'a tu correo electrónico. ' +
            'Continúa en Iniciar sesión ' +
            'para verificar tu cuenta e ingresar tu código.';

          // Actualizar interfaz Angular.
          this.cdr.markForCheck();
        },

        // ==================================
        // ERROR DE REGISTRO
        // ==================================

        error: (err: unknown) => {

          console.error(
            'Error en el registro:',
            err
          );

          // Detener carga.
          this.guardando = false;

          // No afirmar que la cuenta se creó
          // sin confirmación del backend.
          this.cuentaCreada = false;

          // ==================================
          // TIEMPO DE ESPERA AGOTADO
          // ==================================

          if (
            err instanceof Error &&
            err.name === 'TimeoutError'
          ) {

            this.error =
              'El servidor tardó demasiado en responder. ' +
              'Si recibiste el código de verificación ' +
              'en tu correo, puedes continuar desde ' +
              'Iniciar sesión. ' +
              'No vuelvas a registrar la misma cuenta ' +
              'antes de comprobar su estado.';

            this.cdr.markForCheck();

            return;
          }

          // ==================================
          // ERRORES HTTP
          // ==================================

          if (err instanceof HttpErrorResponse) {

            // Error de conexión.
            if (err.status === 0) {

              this.error =
                'No se pudo obtener respuesta de Spring Boot. ' +
                'Si recibiste el correo de verificación, ' +
                'continúa desde Iniciar sesión.';

              this.cdr.markForCheck();

              return;
            }

            // Correo existente.
            if (err.status === 409) {

              this.error = this.mensajeError(
                err,
                'Este correo electrónico ya está registrado. ' +
                'Si tienes un código pendiente, ' +
                'continúa desde Iniciar sesión.'
              );

              this.cdr.markForCheck();

              return;
            }

            // Otros errores de Spring Boot.
            this.error = this.mensajeError(
              err,
              'No fue posible registrar la cuenta.'
            );

            this.cdr.markForCheck();

            return;
          }

          // Error inesperado.
          this.error =
            'Ocurrió un error inesperado al registrar la cuenta.';

          this.cdr.markForCheck();
        }

      });
  }

  // ======================================
  // 3. OBTENER MENSAJE DE ERROR
  // ======================================

  private mensajeError(
    err: HttpErrorResponse,
    mensajeAlternativo: string
  ): string {

    const respuesta: unknown = err.error;

    // Error JSON del backend.
    if (
      respuesta !== null &&
      typeof respuesta === 'object' &&
      'message' in respuesta
    ) {

      const mensaje =
        (respuesta as {
          message?: unknown
        }).message;

      if (
        typeof mensaje === 'string' &&
        mensaje.trim()
      ) {

        return mensaje;
      }
    }

    // Error como texto.
    if (
      typeof respuesta === 'string' &&
      respuesta.trim()
    ) {

      return respuesta;
    }

    return mensajeAlternativo;
  }
}
