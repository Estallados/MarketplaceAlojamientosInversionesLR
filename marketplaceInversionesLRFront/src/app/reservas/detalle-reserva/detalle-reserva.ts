import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Cotizacion } from '../../cotizacion/models/cotizacion';
import {
  claseEtiquetaReserva,
  etiquetaReserva,
  fechaCorta,
  monedaCop,
  puedeCancelarReserva
} from '../models/estado-reserva';
import { Reserva, SimulacionCancelacion } from '../models/reserva';
import { ReservaService } from '../services/reserva.service';
import { MAX_MOTIVO_CANCELACION, mensajeErrorCancelacion } from '../validaciones/errores-cancelacion';
import { CalificarEstancia } from '../../resenas/calificar-estancia/calificar-estancia';

/** Línea del resumen de la cotización. */
export interface LineaDesglose {
  label: string;
  value: string;
}

// ======================================
// DETALLE DE UNA RESERVA DEL HUÉSPED (HU-25, CA-078)
// GET /reservas/{id}: estado actual, fechas, huéspedes, alojamiento,
// política y el desglose congelado de la cotización. Copia la pantalla
// guest/reservation-detail del proyecto base; reseñar (HU-03) y contactar
// al anfitrión (HU-28) quedan para sus historias.
//
// CANCELAR LA RESERVA (HU-01, CA-001, CA-004, CA-007, CA-009)
// Modal del proyecto base con el motivo. Además, antes de confirmar muestra
// la política y la devolución que calcula el backend
// (GET /reservas/{id}/simularcancelacion). Al confirmar envía
// POST /reservas/{id}/cancelar con Idempotency-Key y recarga la reserva para
// mostrar el estado final que registró el backend.
// ======================================

@Component({
  selector: 'app-detalle-reserva',
  standalone: true,
  templateUrl: './detalle-reserva.html',
  imports: [CalificarEstancia]
})
export class DetalleReserva implements OnInit, OnDestroy {

  private readonly router = inject(Router);

  private readonly route = inject(ActivatedRoute);

  private readonly reservaService = inject(ReservaService);

  readonly reserva = signal<Reserva | null>(null);

  readonly loading = signal(true);

  readonly errorCarga = signal(false);

  readonly mensajeError = signal('');

  /** Aviso de éxito que se oculta solo (como en el proyecto base). */
  readonly aviso = signal('');

  private idReserva = 0;

  /** ?accion=cancelar (botón "Cancelar reserva" del listado) abre el modal al cargar. */
  private accionPendiente: string | null = null;

  private temporizadorAviso?: ReturnType<typeof setTimeout>;

  readonly etiqueta = computed(() => {
    const r = this.reserva();
    return r ? etiquetaReserva(r) : '';
  });

  readonly claseEtiqueta = computed(() => claseEtiquetaReserva(this.etiqueta()));

  readonly informacion = computed<{ label: string; value: string | number }[]>(() => {
    const r = this.reserva();
    if (!r) {
      return [];
    }
    return [
      { label: 'Reserva', value: `#${r.idReserva}` },
      { label: 'Estado actual', value: this.etiqueta() },
      { label: 'Fecha de creación', value: fechaCorta(r.fechaCreacion) },
      { label: 'Entrada', value: fechaCorta(r.fechaEntrada) },
      { label: 'Salida', value: fechaCorta(r.fechaSalida) },
      { label: 'Número de noches', value: r.noches },
      { label: 'Huéspedes', value: r.huespedes }
    ];
  });

  /**
   * El backend congela en `desglose` la cotización usada (JSON). Se muestran sus componentes; si no es
   * una cotización, el texto se muestra línea por línea, como en el proyecto base.
   */
  readonly lineasDesglose = computed<LineaDesglose[]>(() => {
    const texto = this.reserva()?.desglose;
    if (!texto) {
      return [];
    }
    const cotizacion = this.cotizacionDe(texto);
    if (!cotizacion) {
      return texto
        .split(/\r?\n/)
        .map(linea => linea.trim())
        .filter(Boolean)
        .map(linea => ({ label: linea, value: '' }));
    }
    const c = cotizacion;
    const lineas: LineaDesglose[] = [
      { label: `${monedaCop(c.precioNoche)} × ${c.noches} ${c.noches === 1 ? 'noche' : 'noches'}`, value: monedaCop(c.precioBase) }
    ];
    if (c.descuento > 0) {
      lineas.push({ label: 'Descuento', value: `−${monedaCop(c.descuento)}` });
    }
    lineas.push(
      { label: `Limpieza (${c.porcentajeLimpieza} %)`, value: monedaCop(c.valorLimpieza) },
      { label: `Tarifa de servicio (${c.porcentajeServicio} %)`, value: monedaCop(c.valorServicio) }
    );
    const servicios = c.serviciosAdicionales ?? [];
    if (servicios.length) {
      servicios.forEach(s => lineas.push({ label: s.nombre, value: monedaCop(s.costo) }));
    } else if (c.valorAdicionales > 0) {
      lineas.push({ label: 'Servicios adicionales', value: monedaCop(c.valorAdicionales) });
    }
    return lineas;
  });

  ngOnInit(): void {

    const idParam = this.route.snapshot.paramMap.get('id');
    const id = Number(idParam);

    if (!idParam || !Number.isInteger(id) || id <= 0) {
      this.errorCarga.set(true);
      this.mensajeError.set('El identificador de la reserva no es válido.');
      this.loading.set(false);
      return;
    }

    this.idReserva = id;
    this.accionPendiente = this.route.snapshot.queryParamMap?.get('accion') ?? null;
    this.cargarReserva();
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorAviso);
  }

  reintentar(): void {
    this.cargarReserva();
  }

  volver(): void {
    void this.router.navigateByUrl('/reservas');
  }

  moneda(valor: number | null | undefined): string {
    return monedaCop(valor);
  }

  fechaCancelacion(): string {
    return fechaCorta(this.reserva()?.cancelacion?.fecha);
  }

  // ==================== RESEÑA (HU-03) ====================

  /** Solo una estadía completada y no cancelada se puede calificar (CA-016). */
  readonly finalizada = computed(() => {
    const r = this.reserva();
    return !!r && !!r.completada && r.estado !== 'CANCELADA';
  });

  readonly tieneResena = computed(() => !!this.reserva()?.tieneResena);

  readonly mostrarResena = signal(false);

  abrirResena(): void {
    if (this.finalizada() && !this.tieneResena()) {
      this.mostrarResena.set(true);
    }
  }

  cerrarResena(): void {
    this.mostrarResena.set(false);
  }

  /** La reseña quedó asociada a la reserva (CA-018): se recarga para actualizar tieneResena. */
  resenaPublicada(): void {
    this.mostrarResena.set(false);
    this.mostrarAviso('¡Gracias! Tu reseña fue publicada.');
    this.cargarReserva(true);
  }

  verResenas(): void {
    void this.router.navigateByUrl('/resenas');
  }

  // ==================== CANCELACIÓN (HU-01) ====================

  readonly maxMotivo = MAX_MOTIVO_CANCELACION;

  /** CONFIRMADA o EN_REVISION que aún no empieza (la misma regla del backend). */
  readonly puedeCancelar = computed(() => {
    const r = this.reserva();
    return !!r && puedeCancelarReserva(r);
  });

  readonly mostrarCancelacion = signal(false);

  readonly motivoCancelacion = signal('');

  readonly cancelando = signal(false);

  readonly errorCancelacion = signal('');

  /** Política y devolución calculadas por el backend antes de confirmar (CA-001). */
  readonly simulacion = signal<SimulacionCancelacion | null>(null);

  readonly cargandoSimulacion = signal(false);

  readonly errorSimulacion = signal('');

  /** Solo tras un error de red: reintentar la MISMA cancelación con la misma clave (RNF-008). */
  private intentoCancelacion?: { motivo: string; clave: string };

  /** Se confirma solo con la devolución ya calculada y un motivo válido. */
  readonly puedeConfirmarCancelacion = computed(() => {
    const motivo = this.motivoCancelacion().trim();
    return !this.cancelando() && !!this.simulacion() && !!motivo && motivo.length <= this.maxMotivo;
  });

  abrirCancelacion(): void {

    this.motivoCancelacion.set('');
    this.errorCancelacion.set('');
    this.mostrarCancelacion.set(true);
    this.cargarSimulacion();
  }

  cerrarCancelacion(): void {

    if (this.cancelando()) {
      return;
    }
    this.mostrarCancelacion.set(false);
  }

  onMotivoCancelacionChange(event: Event): void {
    this.motivoCancelacion.set((event.target as HTMLTextAreaElement).value);
  }

  confirmarCancelacion(): void {

    if (!this.puedeConfirmarCancelacion()) {
      return;
    }

    const motivo = this.motivoCancelacion().trim();
    const clave = this.intentoCancelacion?.motivo === motivo
      ? this.intentoCancelacion.clave
      : this.reservaService.nuevaClaveIdempotencia();

    this.cancelando.set(true);
    this.errorCancelacion.set('');

    this.reservaService.cancelar(this.idReserva, motivo, clave).subscribe({
      next: () => {
        this.intentoCancelacion = undefined;
        this.cancelando.set(false);
        this.mostrarCancelacion.set(false);
        this.mostrarAviso('Tu reserva fue cancelada.');
        // Trae el estado final, la fecha, el motivo y la devolución que registró el backend (CA-004).
        this.cargarReserva(true);
      },
      error: (error: unknown) => {
        this.intentoCancelacion = error instanceof HttpErrorResponse && error.status === 0
          ? { motivo, clave }
          : undefined;
        this.errorCancelacion.set(mensajeErrorCancelacion(error, 'No se pudo cancelar la reserva.'));
        this.cancelando.set(false);
      }
    });
  }

  private cargarSimulacion(): void {

    this.simulacion.set(null);
    this.errorSimulacion.set('');
    this.cargandoSimulacion.set(true);

    this.reservaService.simularCancelacion(this.idReserva).subscribe({
      next: simulacion => {
        this.simulacion.set(simulacion);
        this.cargandoSimulacion.set(false);
      },
      error: (error: unknown) => {
        this.errorSimulacion.set(mensajeErrorCancelacion(error, 'No se pudo calcular la devolución.'));
        this.cargandoSimulacion.set(false);
      }
    });
  }

  private aplicarAccionPendiente(): void {

    const accion = this.accionPendiente;
    if (!accion) {
      return;
    }
    this.accionPendiente = null;
    if (accion === 'cancelar' && this.puedeCancelar()) {
      this.abrirCancelacion();
    }
    if (accion === 'resenar') {
      this.abrirResena();
    }
    // Limpia ?accion= para que no se reabra al refrescar la página.
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { accion: null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  private mostrarAviso(texto: string): void {

    clearTimeout(this.temporizadorAviso);
    this.aviso.set(texto);
    this.temporizadorAviso = setTimeout(() => this.aviso.set(''), 5000);
  }

  /** Si `silencioso` es true no muestra el indicador de carga (se usa al refrescar tras cancelar). */
  private cargarReserva(silencioso = false): void {

    if (!silencioso) {
      this.loading.set(true);
    }
    this.errorCarga.set(false);

    this.reservaService.obtener(this.idReserva).subscribe({
      next: reserva => {
        this.reserva.set(reserva);
        this.loading.set(false);
        this.aplicarAccionPendiente();
      },
      error: (error: HttpErrorResponse) => {
        if (silencioso) {
          return;
        }
        this.errorCarga.set(true);
        this.mensajeError.set(
          error.status === 404
            ? 'La reserva no existe o no tienes acceso a ella.'
            : error.status === 0
              ? 'No se pudo conectar con el servidor. Inténtalo de nuevo.'
              : 'No se pudo cargar la reserva.'
        );
        this.loading.set(false);
      }
    });
  }

  private cotizacionDe(texto: string): Cotizacion | null {
    try {
      const objeto = JSON.parse(texto) as Partial<Cotizacion> | null;
      return objeto && typeof objeto === 'object' && typeof objeto.precioBase === 'number' && typeof objeto.noches === 'number'
        ? (objeto as Cotizacion)
        : null;
    } catch {
      return null;
    }
  }
}
