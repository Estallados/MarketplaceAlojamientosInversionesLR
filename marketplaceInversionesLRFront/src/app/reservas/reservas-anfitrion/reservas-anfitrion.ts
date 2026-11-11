import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { fechaDdMmAaaa, monedaCop } from '../models/estado-reserva';
import { Reserva } from '../models/reserva';
import { ReservaService } from '../services/reserva.service';

// ======================================
// RESERVAS DE MIS ALOJAMIENTOS (HU-25, CA-079)
// GET /reservas/anfitrion: solo las reservas de los alojamientos del
// anfitrión de la sesión, paginadas. Copia host/reservations del proyecto base.
// ======================================

@Component({
  selector: 'app-reservas-anfitrion',
  standalone: true,
  templateUrl: './reservas-anfitrion.html'
})
export class ReservasAnfitrion implements OnInit, OnDestroy {

  private readonly reservaService = inject(ReservaService);

  private readonly router = inject(Router);

  private solicitud?: Subscription;

  readonly tamanio = 10;

  readonly headers = ['Código', 'Huésped', 'Alojamiento', 'Entrada', 'Salida', 'Total', 'Estado'];

  readonly reservations = signal<Reserva[]>([]);

  readonly cargando = signal(false);

  readonly error = signal('');

  readonly pagina = signal(0);

  readonly totalElementos = signal(0);

  readonly totalPaginas = signal(0);

  readonly inicioPagina = computed(() => (this.totalElementos() === 0 ? 0 : this.pagina() * this.tamanio + 1));

  readonly finPagina = computed(() => Math.min((this.pagina() + 1) * this.tamanio, this.totalElementos()));

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.solicitud?.unsubscribe();
  }

  cargar(): void {

    this.solicitud?.unsubscribe();
    this.cargando.set(true);
    this.error.set('');

    this.solicitud = this.reservaService.reservasAnfitrion(this.pagina(), this.tamanio).subscribe({
      next: respuesta => {
        this.reservations.set(respuesta.contenido ?? []);
        this.totalElementos.set(respuesta.totalElementos ?? 0);
        this.totalPaginas.set(respuesta.totalPaginas ?? 0);
        this.cargando.set(false);
      },
      error: (err: unknown) => {
        this.reservations.set([]);
        this.totalElementos.set(0);
        this.totalPaginas.set(0);
        this.cargando.set(false);
        this.error.set(this.mensajeError(err));
      }
    });
  }

  actualizar(): void {
    this.cargar();
  }

  cambiarPagina(nueva: number): void {
    if (this.cargando() || nueva < 0 || nueva >= this.totalPaginas() || nueva === this.pagina()) {
      return;
    }
    this.pagina.set(nueva);
    this.cargar();
  }

  open(id: number): void {
    void this.router.navigate(['/host/reservas', id]);
  }

  etiquetaEstado(r: Reserva): string {
    if (r.estado === 'CONFIRMADA' && r.completada) {
      return 'Finalizada';
    }
    switch (r.estado) {
      case 'CONFIRMADA':
        return 'Confirmada';
      case 'CANCELADA':
        return 'Cancelada';
      case 'EN_REVISION':
        return 'En revisión';
      case 'PENDIENTE_PAGO':
        return 'Pendiente de pago';
      default:
        return r.estado || 'Sin estado';
    }
  }

  badgeClass(r: Reserva): string {
    let tono = 'bg-amber-50 text-amber-800';
    if (r.estado === 'CANCELADA') {
      tono = 'bg-red-50 text-red-700';
    } else if (r.estado === 'EN_REVISION') {
      tono = 'bg-orange-50 text-orange-700';
    } else if (r.estado === 'CONFIRMADA') {
      tono = r.completada ? 'bg-slate-100 text-slate-600' : 'bg-emerald-50 text-emerald-700';
    }
    return `inline-flex rounded-full px-3 py-1 text-xs font-bold ${tono}`;
  }

  fecha(valor: string): string {
    return fechaDdMmAaaa(valor);
  }

  moneda(valor: number): string {
    return monedaCop(valor ?? 0);
  }

  private mensajeError(err: unknown): string {

    if (!(err instanceof HttpErrorResponse)) {
      return 'Ocurrió un error inesperado al consultar las reservas.';
    }
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
    }
    if (err.status === 401) {
      return 'Tu sesión expiró o no has iniciado sesión.';
    }
    if (err.status === 403) {
      return 'No tienes permiso para ver las reservas de un anfitrión.';
    }
    const mensaje = err.error?.message;
    if (typeof mensaje === 'string' && mensaje.trim()) {
      return mensaje;
    }
    return 'No se pudieron consultar las reservas. Inténtalo de nuevo.';
  }
}
