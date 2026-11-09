import {
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  output,
  signal
} from '@angular/core';

import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';

import {
  ConsultaDisponibilidad,
  FechasEstadia
} from '../models/consulta-disponibilidad';
import { DisponibilidadService } from '../services/disponibilidad.service';
import {
  diaSiguiente,
  erroresFechas,
  hoyLocal,
  noches
} from '../validaciones/fechas-estadia';

type CampoFecha = 'llegada' | 'salida';

/**
 * Estado de la consulta:
 * - inicial: sin consultar o las fechas cambiaron desde la última consulta.
 * - consultando: esperando al servidor.
 * - disponible / no-disponible: respuesta 200 del servidor (CA-119, CA-120).
 * - error: fechas rechazadas, alojamiento inexistente, sin conexión o error del servidor.
 */
export type EstadoConsulta =
  | { tipo: 'inicial' }
  | { tipo: 'consultando' }
  | { tipo: 'disponible'; consulta: ConsultaDisponibilidad }
  | { tipo: 'no-disponible'; consulta: ConsultaDisponibilidad }
  | { tipo: 'error'; mensaje: string };

// ======================================
// CONSULTAR DISPONIBILIDAD PARA FECHAS SELECCIONADAS
// HU-21: validación de fechas (CA-082), disponible (CA-119) y no
// disponible (CA-120). Si el alojamiento está disponible lo decide el
// backend; aquí no se calculan cruces de períodos ni de reservas.
// ======================================

@Component({
  selector: 'app-consultar-disponibilidad',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './consultar-disponibilidad.html'
})
export class ConsultarDisponibilidad implements OnInit {

  private readonly disponibilidad =
    inject(DisponibilidadService);

  private readonly route =
    inject(ActivatedRoute);

  private readonly router =
    inject(Router);

  private readonly destroyRef =
    inject(DestroyRef);

  /** Fechas disponibles confirmadas para el siguiente paso (cotización o reserva). */
  readonly continuar = output<FechasEstadia>();

  // ======================================
  // ESTADO
  // ======================================

  readonly idAlojamiento = signal<number | null>(null);

  readonly llegada = signal('');

  readonly salida = signal('');

  readonly estado = signal<EstadoConsulta>({ tipo: 'inicial' });

  readonly hoy = hoyLocal();

  /** La salida mínima es el día siguiente a la llegada. */
  readonly salidaMinima = computed(
    () => diaSiguiente(this.llegada()) || diaSiguiente(this.hoy)
  );

  readonly errores = computed(
    () => erroresFechas(this.llegada(), this.salida(), this.hoy)
  );

  readonly noches = computed(
    () => noches(this.llegada(), this.salida())
  );

  readonly consultando = computed(
    () => this.estado().tipo === 'consultando'
  );

  /** Motivo que envía el servidor cuando no está disponible (CA-120). */
  readonly motivo = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'no-disponible' ? estado.consulta.motivo ?? '' : '';
  });

  readonly textoError = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'error' ? estado.mensaje : '';
  });

  private readonly tocados = signal(new Set<CampoFecha>());

  readonly intentoConsultar = signal(false);

  private peticion: Subscription | null = null;

  // ======================================
  // 1. ALOJAMIENTO Y FECHAS INICIALES
  // ======================================

  ngOnInit(): void {

    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {

        const id = Number(params.get('id'));

        this.cancelarConsulta();

        if (!Number.isSafeInteger(id) || id <= 0) {

          this.idAlojamiento.set(null);

          this.estado.set({
            tipo: 'error',
            mensaje: 'El identificador del alojamiento no es válido.'
          });

          return;
        }

        this.idAlojamiento.set(id);

        this.estado.set({ tipo: 'inicial' });
      });

    // Fechas que llegan en la URL (?llegada=&salida=), por ejemplo al volver.
    const query = this.route.snapshot.queryParamMap;

    this.llegada.set(query.get('llegada') ?? '');
    this.salida.set(query.get('salida') ?? '');
  }

  // ======================================
  // 2. SELECTOR DE FECHAS
  // ======================================

  cambiarFecha(campo: CampoFecha, valor: string): void {

    (campo === 'llegada' ? this.llegada : this.salida).set(valor ?? '');

    this.marcarTocado(campo);

    // Un resultado anterior ya no corresponde a las fechas nuevas.

    this.cancelarConsulta();

    if (this.estado().tipo !== 'inicial' && this.idAlojamiento() !== null) {
      this.estado.set({ tipo: 'inicial' });
    }
  }

  marcarTocado(campo: CampoFecha): void {

    this.tocados.update(actual => new Set(actual).add(campo));
  }

  mostrarError(campo: CampoFecha): boolean {

    return (
      (this.intentoConsultar() || this.tocados().has(campo)) &&
      this.errores()[campo] !== ''
    );
  }

  // ======================================
  // 3. CONSULTAR (CA-082, CA-119, CA-120)
  // GET /alojamientos/{id}/disponibilidad/consultar
  // ======================================

  consultar(): void {

    const id = this.idAlojamiento();

    if (id === null || this.consultando()) {
      return;
    }

    this.intentoConsultar.set(true);

    // Con fechas inválidas no se consulta (CA-082).

    const errores = this.errores();

    if (errores.llegada || errores.salida) {
      this.estado.set({ tipo: 'inicial' });
      return;
    }

    const llegada = this.llegada();
    const salida = this.salida();

    this.estado.set({ tipo: 'consultando' });

    this.peticion = this.disponibilidad
      .consultar(id, llegada, salida)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({

        next: consulta => {

          this.peticion = null;

          // Lo decide el servidor: solo `disponible === true` es disponible.

          this.estado.set(
            consulta.disponible === true
              ? { tipo: 'disponible', consulta }
              : { tipo: 'no-disponible', consulta }
          );

          this.guardarFechasEnUrl(llegada, salida);
        },

        error: err => {

          this.peticion = null;

          this.estado.set({
            tipo: 'error',
            mensaje: this.mensajeError(err)
          });
        }
      });
  }

  // ======================================
  // 4. CONTINUAR CON LA RESERVA (CA-119)
  // ======================================

  /** Solo con un resultado disponible para las fechas actuales. */
  readonly puedeContinuar = computed(() => {

    const estado = this.estado();

    return (
      estado.tipo === 'disponible' &&
      estado.consulta.fechaEntrada === this.llegada() &&
      estado.consulta.fechaSalida === this.salida()
    );
  });

  continuarConReserva(): void {

    const id = this.idAlojamiento();

    if (!this.puedeContinuar() || id === null) {
      return;
    }

    this.continuar.emit({
      idAlojamiento: id,
      llegada: this.llegada(),
      salida: this.salida()
    });
  }

  // ======================================
  // 5. UTILIDADES
  // ======================================

  /** Muestra AAAA-MM-DD como fecha legible sin cambiar de día por la zona horaria. */
  fechaLegible(valor: string): string {

    const [anio, mes, dia] = valor.split('-').map(Number);

    if (!anio || !mes || !dia) {
      return valor;
    }

    return new Date(anio, mes - 1, dia).toLocaleDateString('es-CO', {
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    });
  }

  private cancelarConsulta(): void {

    this.peticion?.unsubscribe();

    this.peticion = null;
  }

  /** Conserva las fechas consultadas en la URL para el siguiente paso. */
  private guardarFechasEnUrl(llegada: string, salida: string): void {

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { llegada, salida },
      queryParamsHandling: 'merge',
      replaceUrl: true
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

      if (err.status === 400) {
        return (
          err.error?.message ??
          'Las fechas no son válidas: la salida debe ser posterior a la llegada.'
        );
      }

      if (err.status === 404) {
        return 'El alojamiento no existe o no está disponible para consulta.';
      }

      return (
        'El servidor no pudo consultar la disponibilidad ' +
        `(HTTP ${err.status}). Inténtalo más tarde.`
      );
    }

    return 'No se pudo consultar la disponibilidad. Inténtalo nuevamente.';
  }
}
