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
  DatePipe
} from '@angular/common';

import {
  takeUntilDestroyed
} from '@angular/core/rxjs-interop';

import {
  finalize,
  timeout
} from 'rxjs';

import { TarifasService } from '../services/tarifas.service';
import { ConfiguracionTarifa } from '../models/configuracion-tarifa';

// ======================================
// CAMPOS DEL FORMULARIO
// ======================================

type CampoTarifa =
  | 'porcentajeLimpieza'
  | 'porcentajeServicio';

// ======================================
// COMPONENTE CONFIGURAR TARIFAS (HU-24)
// ======================================

@Component({
  selector: 'app-tarifas',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe
  ],
  templateUrl: './tarifas.html'
})
export class Tarifas implements OnInit {

  // ======================================
  // SERVICIOS
  // ======================================

  private readonly tarifasService = inject(TarifasService);

  private readonly destroyRef = inject(DestroyRef);

  // ======================================
  // ESTADOS
  // ======================================

  /** Configuración vigente que devolvió el backend. */
  readonly vigente =
    signal<ConfiguracionTarifa | null>(null);

  readonly cargando = signal(false);

  readonly errorCarga = signal(false);

  readonly guardando = signal(false);

  readonly error = signal('');

  readonly mensaje = signal('');

  // ======================================
  // CAMPOS DEL FORMULARIO
  // ======================================

  porcentajeLimpieza: number | null = null;

  porcentajeServicio: number | null = null;

  /** Se activa al intentar guardar para mostrar los errores de todos los campos. */
  intentoGuardar = false;

  // ======================================
  // INICIALIZAR
  // ======================================

  ngOnInit(): void {
    this.cargar();
  }

  // ======================================
  // 1. CONSULTAR PORCENTAJES VIGENTES
  // GET /admin/tarifas
  // ======================================

  cargar(): void {

    this.cargando.set(true);

    this.errorCarga.set(false);

    this.error.set('');

    this.tarifasService.tarifas()
      .pipe(

        timeout(20000),

        finalize(() => {
          this.cargando.set(false);
        }),

        takeUntilDestroyed(this.destroyRef)

      )
      .subscribe({

        next: configuracion => {
          this.aplicar(configuracion);
        },

        error: err => {

          this.errorCarga.set(true);

          this.error.set(
            this.mensajeError(
              err,
              'No se pudieron cargar los porcentajes de tarifas.'
            )
          );

        }

      });

  }

  // ======================================
  // 2. VALIDAR UN CAMPO
  // Contrato: obligatorio, número entre 0 y 100
  // ======================================

  errorCampo(campo: CampoTarifa): string {

    const valor = this[campo];

    if (valor === null || valor === undefined || (valor as unknown) === '') {
      return 'Este campo es obligatorio.';
    }

    if (typeof valor !== 'number' || Number.isNaN(valor)) {
      return 'Ingresa un valor numérico.';
    }

    if (valor < 0) {
      return 'El porcentaje no puede ser negativo.';
    }

    if (valor > 100) {
      return 'El porcentaje no puede ser mayor que 100.';
    }

    if (!/^\d+(\.\d{1,2})?$/.test(String(valor))) {
      return 'Usa máximo dos decimales.';
    }

    return '';

  }

  /** Indica si se debe mostrar el error del campo. */
  mostrarError(
    campo: CampoTarifa,
    tocado: boolean | null
  ): boolean {

    return (
      (this.intentoGuardar || !!tocado) &&
      this.errorCampo(campo) !== ''
    );

  }

  get formularioValido(): boolean {

    return (
      this.errorCampo('porcentajeLimpieza') === '' &&
      this.errorCampo('porcentajeServicio') === ''
    );

  }

  // ======================================
  // 3. GUARDAR PORCENTAJES
  // PUT /admin/tarifas
  // ======================================

  guardar(formulario: NgForm): void {

    if (this.guardando()) {
      return;
    }

    this.intentoGuardar = true;

    this.error.set('');

    this.mensaje.set('');

    if (!this.formularioValido) {

      this.error.set(
        'Revisa los porcentajes: deben ser números entre 0 y 100.'
      );

      return;
    }

    this.guardando.set(true);

    this.tarifasService.actualizarTarifas({
      porcentajeLimpieza: this.porcentajeLimpieza as number,
      porcentajeServicio: this.porcentajeServicio as number
    })
      .pipe(

        timeout(20000),

        finalize(() => {
          this.guardando.set(false);
        }),

        takeUntilDestroyed(this.destroyRef)

      )
      .subscribe({

        next: configuracion => {

          // Reemplazar el estado con los valores
          // que confirmó el backend

          this.aplicar(configuracion);

          this.intentoGuardar = false;

          formulario.form.markAsPristine();

          formulario.form.markAsUntouched();

          this.mensaje.set(
            'Los porcentajes se actualizaron correctamente. ' +
            'Se aplicarán a las cotizaciones nuevas.'
          );

        },

        error: err => {

          this.error.set(
            this.mensajeError(
              err,
              'No fue posible guardar los porcentajes.'
            )
          );

        }

      });

  }

  // ======================================
  // 4. DESCARTAR CAMBIOS
  // ======================================

  restaurar(): void {

    const actual = this.vigente();

    if (actual) {

      this.porcentajeLimpieza = actual.porcentajeLimpieza;

      this.porcentajeServicio = actual.porcentajeServicio;

    }

    this.intentoGuardar = false;

    this.error.set('');

    this.mensaje.set('');

  }

  // ======================================
  // 5. APLICAR CONFIGURACIÓN RECIBIDA
  // ======================================

  private aplicar(
    configuracion: ConfiguracionTarifa
  ): void {

    this.vigente.set(configuracion);

    this.porcentajeLimpieza =
      Number(configuracion.porcentajeLimpieza);

    this.porcentajeServicio =
      Number(configuracion.porcentajeServicio);

  }

  // ======================================
  // 6. MANEJAR ERRORES
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

    // Sin sesión

    if (err.status === 401) {

      return 'Tu sesión expiró. Inicia sesión nuevamente.';

    }

    // Rol sin permiso

    if (err.status === 403) {

      return 'No tienes permisos para configurar las tarifas.';

    }

    // Porcentajes rechazados por el backend

    if (err.status === 400) {

      const detalle =
        err.error?.message ??
        err.error?.mensaje;

      return (
        typeof detalle === 'string' &&
        detalle.trim()
      )
        ? detalle
        : 'Los porcentajes de limpieza y servicio son ' +
        'obligatorios y deben estar entre 0 y 100.';

    }

    return defecto;

  }

}
