import {
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';

import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';

import { Cotizacion } from '../../cotizacion/models/cotizacion';
import { CotizacionService } from '../../cotizacion/services/cotizacion.service';
import { ConsultaDisponibilidad } from '../../disponibilidad/models/consulta-disponibilidad';
import { DisponibilidadService } from '../../disponibilidad/services/disponibilidad.service';
import {
  erroresFechas,
  fechaLegible
} from '../../disponibilidad/validaciones/fechas-estadia';
import { EstadoReserva, Reserva } from '../models/reserva';
import { ReservaService } from '../services/reserva.service';
import {
  ErrorReserva,
  TIPOS_REINTENTABLES,
  TipoErrorReserva,
  clasificarErrorReserva
} from '../validaciones/errores-reserva';

/** Estado de la cotización que se va a reservar. */
export type EstadoCotizacion =
  | { tipo: 'cargando' }
  | { tipo: 'lista'; cotizacion: Cotizacion }
  | { tipo: 'error'; error: ErrorReserva }
  | { tipo: 'sin-datos'; mensaje: string };

/** Estado de la confirmación (POST /reservas). */
export type EstadoConfirmacion =
  | { tipo: 'inicial' }
  | { tipo: 'enviando' }
  | { tipo: 'confirmada'; reserva: Reserva; repetida: boolean }
  | { tipo: 'rechazada'; error: ErrorReserva };

/** Disponibilidad de las fechas consultada de nuevo después de reservar (CA-076). */
export type EstadoBloqueo =
  | { tipo: 'sin-consultar' }
  | { tipo: 'consultando' }
  | { tipo: 'bloqueado'; consulta: ConsultaDisponibilidad }
  | { tipo: 'disponible'; consulta: ConsultaDisponibilidad }
  | { tipo: 'error' };

/** Rechazos después de los cuales no tiene sentido volver a confirmar esta cotización. */
const RECHAZOS_DEFINITIVOS: readonly TipoErrorReserva[] = [
  'no-activo',
  'fechas-ocupadas',
  'cotizacion-vencida',
  'no-encontrado',
  'permiso',
  'sesion',
  // Sin certeza de si se registró: no se ofrece confirmar de nuevo.
  'conflicto'
];

const ETIQUETAS_ESTADO: Record<EstadoReserva, string> = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  CONFIRMADA: 'Confirmada',
  EN_REVISION: 'En revisión',
  CANCELADA: 'Cancelada'
};

const FORMATO_COP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2
});

// ======================================
// CREAR RESERVA CON BLOQUEO AUTOMÁTICO DE FECHAS
// HU-22: CA-070, CA-071, CA-072, CA-074, CA-075, CA-076, CA-086.
// El front cotiza (POST /cotizaciones), muestra el resumen y confirma con
// POST /reservas enviando solo la cotización. La revalidación de la
// disponibilidad, la exclusión de reservas simultáneas, el monto y el
// bloqueo del período los resuelve el backend; aquí no se simulan.
// ======================================

@Component({
  selector: 'app-confirmar-reserva',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './confirmar-reserva.html'
})
export class ConfirmarReserva implements OnInit {

  private readonly cotizaciones = inject(CotizacionService);

  private readonly reservas = inject(ReservaService);

  private readonly disponibilidad = inject(DisponibilidadService);

  private readonly route = inject(ActivatedRoute);

  private readonly destroyRef = inject(DestroyRef);

  // ======================================
  // ESTADO
  // ======================================

  readonly idAlojamiento = signal<number | null>(null);

  readonly llegada = signal('');

  readonly salida = signal('');

  /** Huéspedes pedidos en la URL; null para que el backend use su valor por defecto. */
  private huespedes: number | null = null;

  readonly estadoCotizacion = signal<EstadoCotizacion>({ tipo: 'cargando' });

  readonly confirmacion = signal<EstadoConfirmacion>({ tipo: 'inicial' });

  readonly bloqueo = signal<EstadoBloqueo>({ tipo: 'sin-consultar' });

  /**
   * Idempotency-Key del intento actual (RNF-008). Se conserva para reintentar la misma petición
   * tras un error de conexión o del servidor, y se cambia cuando la petición tuvo una respuesta
   * definitiva o hay una cotización nueva.
   */
  private claveIdempotencia: string | null = null;

  private peticionCotizacion: Subscription | null = null;

  // ======================================
  // DERIVADOS
  // ======================================

  /** Cotización vigente mostrada en el resumen; el monto no se puede editar (CA-086). */
  readonly cotizacion = computed(() => {
    const estado = this.estadoCotizacion();
    return estado.tipo === 'lista' ? estado.cotizacion : null;
  });

  readonly enviando = computed(() => this.confirmacion().tipo === 'enviando');

  readonly reserva = computed(() => {
    const estado = this.confirmacion();
    return estado.tipo === 'confirmada' ? estado.reserva : null;
  });

  readonly reservaRepetida = computed(() => {
    const estado = this.confirmacion();
    return estado.tipo === 'confirmada' && estado.repetida;
  });

  /** Error de la cotización o de la confirmación. */
  readonly error = computed<ErrorReserva | null>(() => {
    const confirmacion = this.confirmacion();
    if (confirmacion.tipo === 'rechazada') {
      return confirmacion.error;
    }
    const cotizacion = this.estadoCotizacion();
    return cotizacion.tipo === 'error' ? cotizacion.error : null;
  });

  readonly puedeConfirmar = computed(() => {
    const confirmacion = this.confirmacion();
    if (this.cotizacion() === null) {
      return false;
    }
    if (confirmacion.tipo === 'inicial') {
      return true;
    }
    return (
      confirmacion.tipo === 'rechazada' &&
      !RECHAZOS_DEFINITIVOS.includes(confirmacion.error.tipo)
    );
  });

  readonly esReintento = computed(() => {
    const confirmacion = this.confirmacion();
    return (
      confirmacion.tipo === 'rechazada' &&
      TIPOS_REINTENTABLES.includes(confirmacion.error.tipo)
    );
  });

  /** Ruta para volver a elegir fechas (HU-21). */
  readonly rutaFechas = computed(() => {
    const id = this.idAlojamiento();
    return id === null ? null : ['/alojamientos', id, 'consultar-disponibilidad'];
  });

  readonly queryFechas = computed(() => {
    const params: Record<string, string> = {};
    if (this.llegada()) {
      params['llegada'] = this.llegada();
    }
    if (this.salida()) {
      params['salida'] = this.salida();
    }
    return params;
  });

  // ======================================
  // 1. DATOS DE LA URL Y COTIZACIÓN
  // /alojamientos/:id/reservar?llegada=&salida=&huespedes=
  // ======================================

  ngOnInit(): void {

    const id = Number(this.route.snapshot.paramMap.get('id'));
    const query = this.route.snapshot.queryParamMap;

    this.llegada.set(query.get('llegada') ?? '');
    this.salida.set(query.get('salida') ?? '');

    const huespedes = Number(query.get('huespedes'));
    this.huespedes =
      query.has('huespedes') && Number.isSafeInteger(huespedes) && huespedes > 0
        ? huespedes
        : null;

    if (!Number.isSafeInteger(id) || id <= 0) {
      this.estadoCotizacion.set({
        tipo: 'sin-datos',
        mensaje: 'El identificador del alojamiento no es válido.'
      });
      return;
    }

    this.idAlojamiento.set(id);

    const errores = erroresFechas(this.llegada(), this.salida());

    if (errores.llegada || errores.salida) {
      this.estadoCotizacion.set({
        tipo: 'sin-datos',
        mensaje:
          'Faltan las fechas de la estadía o no son válidas. ' +
          'Consulta la disponibilidad para elegirlas.'
      });
      return;
    }

    this.cotizar();
  }

  /**
   * Pide una cotización nueva (POST /cotizaciones). También se usa cuando la anterior venció.
   * Cada cotización nueva empieza un intento de reserva nuevo, con su propia clave.
   */
  cotizar(): void {

    const id = this.idAlojamiento();

    if (id === null || this.enviando()) {
      return;
    }

    this.peticionCotizacion?.unsubscribe();

    this.estadoCotizacion.set({ tipo: 'cargando' });
    this.confirmacion.set({ tipo: 'inicial' });
    this.bloqueo.set({ tipo: 'sin-consultar' });
    this.claveIdempotencia = null;

    this.peticionCotizacion = this.cotizaciones
      .cotizar({
        idAlojamiento: id,
        fechaEntrada: this.llegada(),
        fechaSalida: this.salida(),
        ...(this.huespedes !== null ? { huespedes: this.huespedes } : {})
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: cotizacion => {
          this.peticionCotizacion = null;
          this.estadoCotizacion.set({ tipo: 'lista', cotizacion });
          this.claveIdempotencia = this.reservas.nuevaClaveIdempotencia();
        },
        error: err => {
          this.peticionCotizacion = null;
          this.estadoCotizacion.set({
            tipo: 'error',
            error: clasificarErrorReserva(err)
          });
        }
      });
  }

  // ======================================
  // 2. CONFIRMAR (CA-070, CA-074, CA-075, CA-086)
  // POST /reservas con { idCotizacion } y la cabecera Idempotency-Key
  // ======================================

  confirmar(): void {

    const cotizacion = this.cotizacion();

    // Un solo envío a la vez: el estado cambia antes de suscribirse.
    if (!cotizacion || !this.puedeConfirmar()) {
      return;
    }

    const clave =
      this.claveIdempotencia ?? this.reservas.nuevaClaveIdempotencia();

    this.claveIdempotencia = clave;

    this.confirmacion.set({ tipo: 'enviando' });

    this.reservas
      // Solo la cotización: el backend toma el monto de ella (CA-086).
      .crear({ idCotizacion: cotizacion.idCotizacion }, clave)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ reserva, repetida }) => {
          this.confirmacion.set({ tipo: 'confirmada', reserva, repetida });
          this.actualizarDisponibilidad(reserva);
        },
        error: err => {
          const error = clasificarErrorReserva(err);

          // Con una respuesta definitiva, el siguiente intento es otra petición.
          if (!TIPOS_REINTENTABLES.includes(error.tipo)) {
            this.claveIdempotencia = null;
          }

          this.confirmacion.set({ tipo: 'rechazada', error });
        }
      });
  }

  // ======================================
  // 3. DISPONIBILIDAD DESPUÉS DE RESERVAR (CA-076)
  // El bloqueo lo hace el backend; aquí solo se vuelve a consultar
  // (HU-21) para no dejar en pantalla una disponibilidad desactualizada.
  // ======================================

  private actualizarDisponibilidad(reserva: Reserva): void {

    this.bloqueo.set({ tipo: 'consultando' });

    this.disponibilidad
      .consultar(reserva.idAlojamiento, reserva.fechaEntrada, reserva.fechaSalida)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: consulta =>
          this.bloqueo.set(
            consulta.disponible === true
              ? { tipo: 'disponible', consulta }
              : { tipo: 'bloqueado', consulta }
          ),
        error: () => this.bloqueo.set({ tipo: 'error' })
      });
  }

  // ======================================
  // 4. PRESENTACIÓN
  // ======================================

  fechaLegible(valor: string): string {
    return fechaLegible(valor);
  }

  moneda(valor: number | null | undefined): string {
    return typeof valor === 'number' ? FORMATO_COP.format(valor) : '';
  }

  etiquetaEstado(estado: EstadoReserva): string {
    return ETIQUETAS_ESTADO[estado] ?? estado;
  }

  /** Hora de vencimiento (HH:mm) tal como la envía el backend, sin convertir zona horaria. */
  horaExpiracion(valor: string | undefined): string {
    const hora = /T(\d{2}:\d{2})/.exec(valor ?? '');
    return hora ? hora[1] : '';
  }
}
