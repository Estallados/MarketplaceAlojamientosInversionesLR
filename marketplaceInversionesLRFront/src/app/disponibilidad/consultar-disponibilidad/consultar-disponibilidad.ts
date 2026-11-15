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
import { CalendarioDisponibilidad } from '../../alojamientos/models/disponibilidad';
import {
  diaSiguiente,
  erroresFechas,
  fechaLegible,
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

/** Estado de una noche en el calendario. La salida no ocupa noche. */
export type EstadoDia = 'pasado' | 'reservado' | 'disponible' | 'no-disponible';

export interface DiaCalendario {

  numero: number;

  /** AAAA-MM-DD */
  fecha: string;

  estado: EstadoDia;
}

const DIAS_SEMANA = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

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
  // CALENDARIO (períodos ofrecidos y reservas)
  // ======================================

  readonly diasSemana = DIAS_SEMANA;

  readonly estadosLeyenda: EstadoDia[] = ['disponible', 'reservado', 'no-disponible', 'pasado'];

  readonly calendarioDatos = signal<CalendarioDisponibilidad | null>(null);

  readonly calendarioCargando = signal(false);

  readonly calendarioError = signal('');

  /** Primer día del mes que se muestra. */
  readonly mesActual = signal(primerDiaDelMes(this.hoy));

  private peticionCalendario: Subscription | null = null;

  readonly tituloMes = computed(() => {

    const [anio, mes] = this.mesActual().split('-').map(Number);

    const texto = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' })
      .format(new Date(anio, mes - 1, 1));

    return texto.charAt(0).toUpperCase() + texto.slice(1);
  });

  /** No se navega a meses anteriores al actual. */
  readonly puedeRetroceder = computed(
    () => this.mesActual() > primerDiaDelMes(this.hoy)
  );

  /**
   * Celdas del mes (null para los huecos antes del día 1). Una noche está disponible si cae en
   * [inicio, fin) de un período y no en [entrada, salida) de una reserva, igual que en el backend.
   */
  readonly calendario = computed<(DiaCalendario | null)[]>(() => {

    const datos = this.calendarioDatos();

    const periodos = datos?.periodos ?? [];

    const reservados = datos?.reservados ?? [];

    const [anio, mes] = this.mesActual().split('-').map(Number);

    const desplazamiento = (new Date(anio, mes - 1, 1).getDay() + 6) % 7;

    const diasMes = new Date(anio, mes, 0).getDate();

    const celdas: (DiaCalendario | null)[] = Array(desplazamiento).fill(null);

    for (let numero = 1; numero <= diasMes; numero++) {

      const fecha = `${anio}-${dosDigitos(mes)}-${dosDigitos(numero)}`;

      const estado: EstadoDia =
        fecha < this.hoy
          ? 'pasado'
          : reservados.some(r => fecha >= r.fechaEntrada && fecha < r.fechaSalida)
            ? 'reservado'
            : periodos.some(p => fecha >= p.fechaInicio && fecha < p.fechaFin)
              ? 'disponible'
              : 'no-disponible';

      celdas.push({ numero, fecha, estado });
    }

    return celdas;
  });

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

        this.cargarCalendario(id);
      });

    // Fechas que llegan en la URL (?llegada=&salida=), por ejemplo al volver.
    const query = this.route.snapshot.queryParamMap;

    this.llegada.set(query.get('llegada') ?? '');
    this.salida.set(query.get('salida') ?? '');

    // El calendario abre en el mes de la llegada si ya viene elegida.
    if (this.llegada() >= this.hoy && !erroresFechas(this.llegada(), '', this.hoy).llegada) {
      this.mesActual.set(primerDiaDelMes(this.llegada()));
    }
  }

  // ======================================
  // 2. SELECTOR DE FECHAS
  // ======================================

  cambiarFecha(campo: CampoFecha, valor: string): void {

    this.aplicarFecha(campo, valor);

    this.marcarTocado(campo);
  }

  private aplicarFecha(campo: CampoFecha, valor: string): void {

    (campo === 'llegada' ? this.llegada : this.salida).set(valor ?? '');

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
  // 2.1 CALENDARIO
  // GET /alojamientos/{id}/disponibilidad
  // ======================================

  private cargarCalendario(id: number): void {

    this.peticionCalendario?.unsubscribe();

    this.calendarioDatos.set(null);
    this.calendarioError.set('');
    this.calendarioCargando.set(true);

    this.peticionCalendario = this.disponibilidad
      .calendario(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: datos => {
          this.peticionCalendario = null;
          this.calendarioCargando.set(false);
          this.calendarioDatos.set(datos);
        },
        error: err => {
          this.peticionCalendario = null;
          this.calendarioCargando.set(false);
          this.calendarioError.set(
            err instanceof HttpErrorResponse && err.status === 404
              ? 'El alojamiento no existe o no está disponible para consulta.'
              : 'No se pudo cargar el calendario. Puedes consultar escribiendo las fechas.'
          );
        }
      });
  }

  cambiarMes(pasos: number): void {

    if (pasos < 0 && !this.puedeRetroceder()) {
      return;
    }

    const [anio, mes] = this.mesActual().split('-').map(Number);

    const nuevo = new Date(anio, mes - 1 + pasos, 1);

    this.mesActual.set(`${nuevo.getFullYear()}-${dosDigitos(nuevo.getMonth() + 1)}-01`);
  }

  /**
   * Primer clic: llegada (solo en una noche disponible). Segundo clic en un día posterior: salida
   * (puede ser cualquier día, porque la salida no ocupa noche). Un clic más empieza de nuevo.
   */
  seleccionarDia(dia: DiaCalendario): void {

    if (dia.estado === 'pasado') {
      return;
    }

    const llegada = this.llegada();

    if (llegada && !this.salida() && dia.fecha > llegada) {
      this.aplicarFecha('salida', dia.fecha);
      this.marcarTocado('llegada');
      this.marcarTocado('salida');
      return;
    }

    if (dia.estado !== 'disponible') {
      return;
    }

    this.aplicarFecha('llegada', dia.fecha);
    this.aplicarFecha('salida', '');
  }

  /** El día está dentro de la estadía elegida (llegada incluida, salida incluida como extremo). */
  enSeleccion(dia: DiaCalendario): boolean {

    const llegada = this.llegada();
    const salida = this.salida();

    if (!llegada) {
      return false;
    }

    return salida
      ? dia.fecha >= llegada && dia.fecha <= salida
      : dia.fecha === llegada;
  }

  /**
   * Cada estado se distingue sin depender del color (daltonismo, p. ej. protanopia): por el brillo
   * del relleno, el borde, un patrón y un símbolo.
   * - disponible: relleno oscuro sólido, texto blanco y ✓
   * - reservado: rayas diagonales, número tachado y ✕
   * - no disponible: fondo blanco con borde punteado y –
   * - pasado: atenuado
   */
  claseDia(dia: DiaCalendario): string {

    const estados: Record<EstadoDia, string> = {
      pasado:
        'cursor-default border-transparent bg-ink-50 text-ink-400 opacity-60',
      reservado:
        'border-2 border-orange-700 text-orange-950 ' +
        'bg-[repeating-linear-gradient(135deg,var(--color-orange-200)_0_5px,var(--color-orange-50)_5px_10px)]',
      disponible:
        'border-2 border-brand-900 bg-brand-700 text-white hover:bg-brand-800',
      'no-disponible':
        'border-2 border-dashed border-ink-300 bg-white text-ink-500'
    };

    const seleccion = this.enSeleccion(dia)
      ? ' ring-4 ring-ink-900 ring-offset-2'
      : '';

    return `flex min-h-12 flex-col justify-between rounded-md p-1.5 text-left text-sm transition sm:min-h-16 sm:p-2 ${estados[dia.estado]}${seleccion}`;
  }

  /** Símbolo que acompaña al color de cada estado. */
  simboloDia(estado: EstadoDia): string {

    return {
      pasado: '',
      reservado: '✕',
      disponible: '✓',
      'no-disponible': '–'
    }[estado];
  }

  etiquetaDia(estado: EstadoDia): string {

    return {
      pasado: 'Pasado',
      reservado: 'Reservado',
      disponible: 'Disponible',
      'no-disponible': 'No disponible'
    }[estado];
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

    const fechas: FechasEstadia = {
      idAlojamiento: id,
      llegada: this.llegada(),
      salida: this.salida()
    };

    this.continuar.emit(fechas);

    // Siguiente paso: cotizar y confirmar la reserva (HU-22).
    void this.router.navigate(
      ['/alojamientos', id, 'reservar'],
      { queryParams: { llegada: fechas.llegada, salida: fechas.salida } }
    );
  }

  // ======================================
  // 5. UTILIDADES
  // ======================================

  /** Muestra AAAA-MM-DD como fecha legible sin cambiar de día por la zona horaria. */
  fechaLegible(valor: string): string {

    return fechaLegible(valor);
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
          'Las fechas son inválidas: la salida debe ser posterior a la llegada.'
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

/** AAAA-MM-01 del mes de la fecha AAAA-MM-DD. */
function primerDiaDelMes(fecha: string): string {
  return `${fecha.slice(0, 7)}-01`;
}

function dosDigitos(valor: number): string {
  return String(valor).padStart(2, '0');
}
