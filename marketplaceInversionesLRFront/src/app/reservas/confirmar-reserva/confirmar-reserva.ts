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

import { DetalleAlojamiento } from '../../alojamientos/models/detalle-alojamiento';
import { DetalleAlojamientoService } from '../../alojamientos/services/detalle-alojamiento.service';
import { Cotizacion, ServicioCotizado } from '../../cotizacion/models/cotizacion';
import { CotizacionService } from '../../cotizacion/services/cotizacion.service';
import { ConsultaDisponibilidad } from '../../disponibilidad/models/consulta-disponibilidad';
import { DisponibilidadService } from '../../disponibilidad/services/disponibilidad.service';
import {
  erroresFechas,
  fechaLegible
} from '../../disponibilidad/validaciones/fechas-estadia';
import { EstadoReserva, Reserva } from '../models/reserva';
import { PagoReserva } from '../pago-reserva/pago-reserva';
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

/** Espera tras el último cambio de huéspedes o servicios antes de recotizar. */
export const ESPERA_RECOTIZAR_MS = 400;

const ETIQUETAS_PAGO: Record<string, string> = {
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
  ERROR: 'No confirmado',
  PENDIENTE: 'Pendiente',
  REEMBOLSADO: 'Reembolsado'
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
// HU-31: el huésped elige huéspedes y servicios adicionales y la cotización
// se pide de nuevo al backend con cada cambio (CA-080, CA-083, CA-084,
// CA-179, CA-183). El front no calcula ningún valor.
// HU-27: "Confirmar reserva" abre el modal "Confirma y paga" para elegir un
// método de pago de prueba; POST /reservas lo cobra en ambiente de prueba
// (CA-160) y la pantalla muestra el pago registrado (CA-161) o el rechazo,
// que permite intentar con otro método (CA-162).
// El front cotiza (POST /cotizaciones), muestra el resumen y confirma con
// POST /reservas enviando solo la cotización. La revalidación de la
// disponibilidad, la exclusión de reservas simultáneas, el monto y el
// bloqueo del período los resuelve el backend; aquí no se simulan.
// ======================================

@Component({
  selector: 'app-confirmar-reserva',
  standalone: true,
  imports: [RouterLink, PagoReserva],
  templateUrl: './confirmar-reserva.html'
})
export class ConfirmarReserva implements OnInit {

  private readonly cotizaciones = inject(CotizacionService);

  private readonly reservas = inject(ReservaService);

  private readonly disponibilidad = inject(DisponibilidadService);

  private readonly alojamientos = inject(DetalleAlojamientoService);

  private readonly route = inject(ActivatedRoute);

  private readonly destroyRef = inject(DestroyRef);

  // ======================================
  // ESTADO
  // ======================================

  readonly idAlojamiento = signal<number | null>(null);

  readonly llegada = signal('');

  readonly salida = signal('');

  /** Huéspedes elegidos (o pedidos en la URL); null para que el backend use su valor por defecto. */
  readonly huespedes = signal<number | null>(null);

  /** Servicios adicionales elegidos (HU-31). */
  readonly serviciosElegidos = signal<number[]>([]);

  /** Alojamiento (GET /alojamientos/{id}): capacidad y servicios para elegir. */
  readonly alojamiento = signal<DetalleAlojamiento | null>(null);

  /** Servicios que devolvió la última cotización (serviciosDisponibles). */
  private readonly serviciosCotizados = signal<ServicioCotizado[] | null>(null);

  /** true desde que se cambian huéspedes o servicios hasta que llega la cotización nueva. */
  readonly recotizando = signal(false);

  private temporizadorRecotizar: ReturnType<typeof setTimeout> | null = null;

  readonly estadoCotizacion = signal<EstadoCotizacion>({ tipo: 'cargando' });

  readonly confirmacion = signal<EstadoConfirmacion>({ tipo: 'inicial' });

  readonly bloqueo = signal<EstadoBloqueo>({ tipo: 'sin-consultar' });

  /**
   * Idempotency-Key del intento actual (RNF-008). Se conserva para reintentar la misma petición
   * tras un error de conexión o del servidor, y se cambia cuando la petición tuvo una respuesta
   * definitiva o hay una cotización nueva.
   */
  private claveIdempotencia: string | null = null;

  /** Método de pago enviado con la clave actual: la clave solo se repite con el mismo método. */
  private metodoIntento: string | null = null;

  /** Modal "Confirma y paga" abierto (HU-27). */
  readonly pagoAbierto = signal(false);

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
    // No se confirma una cotización que ya no corresponde a lo elegido.
    if (this.cotizacion() === null || this.recotizando()) {
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

  /** Servicios que se pueden elegir: los de la última cotización o, si aún no hay, los del alojamiento. */
  readonly serviciosDisponibles = computed<ServicioCotizado[]>(() =>
    (this.serviciosCotizados() ?? this.alojamiento()?.serviciosAdicionales ?? [])
      .filter((s): s is ServicioCotizado => typeof s.idServicio === 'number')
  );

  /** Se puede personalizar la estadía mientras la reserva no se haya creado. */
  readonly personalizable = computed(() =>
    this.idAlojamiento() !== null &&
    this.estadoCotizacion().tipo !== 'sin-datos' &&
    this.reserva() === null
  );

  /** Capacidad del alojamiento; null si no se pudo consultar. */
  readonly capacidad = computed(() => {
    const capacidad = this.alojamiento()?.capacidad;
    return typeof capacidad === 'number' && capacidad > 0 ? Math.floor(capacidad) : null;
  });

  /**
   * Opciones del selector de huéspedes: de 1 a la capacidad. Si en la URL llegó un número mayor, también
   * se incluye para que se vea lo pedido junto con el rechazo del backend (CA-083).
   */
  readonly opcionesHuespedes = computed(() => {
    const maximo = Math.max(this.capacidad() ?? 0, this.huespedes() ?? 1);
    return Array.from({ length: maximo }, (_, i) => i + 1);
  });

  /** Huéspedes que muestra el selector: los elegidos, los cotizados o 1 (valor por defecto del backend). */
  readonly huespedesMostrados = computed(() => this.huespedes() ?? this.cotizacion()?.huespedes ?? 1);

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
    this.huespedes.set(
      query.has('huespedes') && Number.isSafeInteger(huespedes) && huespedes > 0
        ? huespedes
        : null
    );

    this.destroyRef.onDestroy(() => this.cancelarRecotizacion());

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

    this.cargarAlojamiento(id);
    this.cotizar();
  }

  /**
   * Pide una cotización nueva (POST /cotizaciones). También se usa cuando la anterior venció.
   * Cada cotización nueva empieza un intento de reserva nuevo, con su propia clave.
   */
  cotizar(conservarResumen = false): void {

    const id = this.idAlojamiento();

    if (id === null || this.enviando()) {
      return;
    }

    this.cancelarRecotizacion();
    this.peticionCotizacion?.unsubscribe();

    // Al recotizar se deja el resumen anterior (atenuado) hasta que llegue el nuevo.
    if (!(conservarResumen && this.cotizacion() !== null)) {
      this.estadoCotizacion.set({ tipo: 'cargando' });
    }
    this.recotizando.set(conservarResumen);
    this.confirmacion.set({ tipo: 'inicial' });
    this.bloqueo.set({ tipo: 'sin-consultar' });
    this.claveIdempotencia = null;

    this.peticionCotizacion = this.cotizaciones
      .cotizar({
        idAlojamiento: id,
        fechaEntrada: this.llegada(),
        fechaSalida: this.salida(),
        ...(this.huespedes() !== null ? { huespedes: this.huespedes()! } : {}),
        // Solo los elegidos: el backend suma únicamente estos (CA-183). Sin elegidos se omite.
        ...(this.serviciosElegidos().length > 0
          ? { serviciosSeleccionados: this.serviciosElegidos() }
          : {})
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: cotizacion => {
          this.peticionCotizacion = null;
          this.recotizando.set(false);
          if (cotizacion.serviciosDisponibles) {
            this.serviciosCotizados.set(cotizacion.serviciosDisponibles);
          }
          this.estadoCotizacion.set({ tipo: 'lista', cotizacion });
          this.claveIdempotencia = this.reservas.nuevaClaveIdempotencia();
        },
        error: err => {
          this.peticionCotizacion = null;
          this.recotizando.set(false);
          this.estadoCotizacion.set({
            tipo: 'error',
            error: clasificarErrorReserva(err)
          });
        }
      });
  }

  // ======================================
  // 1.1 PERSONALIZAR LA ESTADÍA (HU-31)
  // Cada cambio pide una cotización nueva al backend tras una pausa corta.
  // ======================================

  cambiarHuespedes(valor: number | string): void {

    const huespedes = Number(valor);

    if (!Number.isSafeInteger(huespedes) || huespedes <= 0 || huespedes === this.huespedes()) {
      return;
    }

    this.huespedes.set(huespedes);
    this.programarRecotizacion();
  }

  servicioElegido(idServicio: number): boolean {
    return this.serviciosElegidos().includes(idServicio);
  }

  toggleServicio(idServicio: number, elegido: boolean): void {

    if (elegido === this.servicioElegido(idServicio)) {
      return;
    }

    this.serviciosElegidos.update(actuales =>
      elegido ? [...actuales, idServicio] : actuales.filter(id => id !== idServicio)
    );
    this.programarRecotizacion();
  }

  private programarRecotizacion(): void {

    if (this.enviando() || this.reserva() !== null) {
      return;
    }

    this.cancelarRecotizacion();
    this.recotizando.set(true);

    this.temporizadorRecotizar = setTimeout(() => {
      this.temporizadorRecotizar = null;
      this.cotizar(true);
    }, ESPERA_RECOTIZAR_MS);
  }

  private cancelarRecotizacion(): void {

    if (this.temporizadorRecotizar !== null) {
      clearTimeout(this.temporizadorRecotizar);
      this.temporizadorRecotizar = null;
    }
  }

  /** Capacidad y servicios del alojamiento. Si falla, se usan los servicios que devuelva la cotización. */
  private cargarAlojamiento(id: number): void {

    this.alojamientos
      .obtener(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: alojamiento => this.alojamiento.set(alojamiento),
        error: () => this.alojamiento.set(null)
      });
  }

  // ======================================
  // 2. CONFIRMAR Y PAGAR (CA-070, CA-074, CA-075, CA-086, HU-27)
  // POST /reservas con { idCotizacion, metodoPago } y la cabecera Idempotency-Key
  // ======================================

  /** Botón de la página: abre el modal de pago o, tras un error de conexión, repite la misma petición. */
  iniciarConfirmacion(): void {

    if (!this.puedeConfirmar()) {
      return;
    }

    if (this.esReintento() && this.metodoIntento !== null) {
      this.confirmar(this.metodoIntento);
      return;
    }

    this.pagoAbierto.set(true);
  }

  cerrarPago(): void {
    if (!this.enviando()) {
      this.pagoAbierto.set(false);
    }
  }

  /** Motivo del rechazo del pago que se muestra dentro del modal (CA-162). */
  readonly errorPago = computed(() => {
    const confirmacion = this.confirmacion();
    if (confirmacion.tipo !== 'rechazada' || confirmacion.error.tipo !== 'pago-rechazado') {
      return null;
    }
    return confirmacion.error.detalle || confirmacion.error.mensaje;
  });

  /**
   * @param metodoPago método de pago de prueba elegido en el modal (HU-27)
   */
  confirmar(metodoPago: string): void {

    const cotizacion = this.cotizacion();

    // Un solo envío a la vez: el estado cambia antes de suscribirse.
    if (!cotizacion || !this.puedeConfirmar()) {
      return;
    }

    // Con otro método es otra petición: no se reutiliza la clave del intento anterior.
    if (this.metodoIntento !== metodoPago) {
      this.claveIdempotencia = null;
    }

    const clave =
      this.claveIdempotencia ?? this.reservas.nuevaClaveIdempotencia();

    this.claveIdempotencia = clave;
    this.metodoIntento = metodoPago;

    this.confirmacion.set({ tipo: 'enviando' });

    this.reservas
      // Solo la cotización y el método: el backend toma el monto de ella (CA-086).
      .crear({ idCotizacion: cotizacion.idCotizacion, metodoPago }, clave)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ reserva, repetida }) => {
          this.pagoAbierto.set(false);
          this.confirmacion.set({ tipo: 'confirmada', reserva, repetida });
          this.actualizarDisponibilidad(reserva);
        },
        error: err => {
          const error = clasificarErrorReserva(err);

          // Con una respuesta definitiva, el siguiente intento es otra petición.
          if (!TIPOS_REINTENTABLES.includes(error.tipo)) {
            this.claveIdempotencia = null;
            this.metodoIntento = null;
          }

          // Un pago rechazado se informa en el modal para elegir otro método (CA-162);
          // los demás errores se muestran en la página.
          if (error.tipo !== 'pago-rechazado') {
            this.pagoAbierto.set(false);
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

  /** Texto del proveedor del pago: siempre un ambiente de prueba (CA-160). */
  proveedorPago(proveedor: string | undefined): string {
    if (proveedor === 'STRIPE_TEST') {
      return 'Stripe (modo de prueba)';
    }
    return proveedor === 'SIMULADO' ? 'Pago simulado' : (proveedor ?? '');
  }

  etiquetaPago(estado: string | undefined): string {
    return ETIQUETAS_PAGO[estado ?? ''] ?? (estado ?? '');
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
