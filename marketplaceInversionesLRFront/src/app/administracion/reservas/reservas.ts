import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { ReservaAdminVista } from '../models/reserva-admin';
import { AdminReservasService } from '../services/admin-reservas.service';

/** Reservas por página. */
const TAMANIO = 20;

/**
 * Administración de reservas (HU-11): por defecto muestra las reservas EN_REVISION, que son las que
 * quedaron marcadas para revisión manual al bloquear un alojamiento (CA-112).
 */
@Component({
  selector: 'app-admin-reservas',
  standalone: true,
  templateUrl: './reservas.html',
  imports: [CurrencyPipe, DatePipe],
})
export class ReservasAdmin implements OnInit {
  private readonly admin = inject(AdminReservasService);

  readonly headers = ['Código', 'Huésped', 'Alojamiento', 'Anfitrión', 'Fecha', 'Total', 'Estado'];
  /** Filtros (Finalizada no es un estado: es una confirmada ya cumplida). */
  readonly filtros = [
    { valor: '', texto: 'Todos los estados' },
    { valor: 'EN_REVISION', texto: 'En revisión' },
    { valor: 'CONFIRMADA', texto: 'Confirmadas' },
    { valor: 'PENDIENTE_PAGO', texto: 'Pendientes de pago' },
    { valor: 'CANCELADA', texto: 'Canceladas' },
  ];

  readonly todas = signal<ReservaAdminVista[]>([]);
  readonly filtroEstado = signal('EN_REVISION');
  readonly pagina = signal(0);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);

  readonly filtradas = computed(() => {
    const estado = this.filtroEstado();
    return estado ? this.todas().filter((r) => r.estado === estado) : this.todas();
  });
  readonly enRevision = computed(() => this.todas().filter((r) => r.estado === 'EN_REVISION').length);
  readonly totalElementos = computed(() => this.filtradas().length);
  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.totalElementos() / TAMANIO)));
  readonly reservations = computed(() => {
    const inicio = this.pagina() * TAMANIO;
    return this.filtradas().slice(inicio, inicio + TAMANIO);
  });

  /** Hasta 5 números de página alrededor de la actual. */
  readonly paginasVisibles = computed(() => {
    const total = this.totalPaginas();
    const inicio = Math.max(0, Math.min(this.pagina() - 2, total - 5));
    const fin = Math.min(total, inicio + 5);
    return Array.from({ length: fin - inicio }, (_, i) => inicio + i);
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    this.errorCarga.set(false);
    this.admin.todas().subscribe({
      next: (lista) => {
        this.todas.set(lista ?? []);
        this.pagina.set(0);
        this.loading.set(false);
      },
      error: () => {
        this.todas.set([]);
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }

  cambiarEstado(valor: string): void {
    this.filtroEstado.set(valor);
    this.pagina.set(0);
  }

  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPaginas() && pagina !== this.pagina()) {
      this.pagina.set(pagina);
    }
  }

  etiqueta(r: ReservaAdminVista): string {
    if (r.estado === 'CONFIRMADA') return r.completada ? 'Finalizada' : 'Confirmada';
    if (r.estado === 'CANCELADA') return 'Cancelada';
    if (r.estado === 'EN_REVISION') return 'En revisión';
    if (r.estado === 'PENDIENTE_PAGO') return 'Pendiente de pago';
    return r.estado;
  }

  badgeClass(r: ReservaAdminVista): string {
    let tone = 'bg-amber-50 text-amber-700';
    if (r.estado === 'CANCELADA') {
      tone = 'bg-red-50 text-red-700';
    } else if (r.estado === 'CONFIRMADA') {
      tone = r.completada ? 'bg-ink-100 text-ink-600' : 'bg-brand-50 text-brand-700';
    }
    return 'inline-flex rounded-full px-3 py-1 text-xs font-bold ' + tone;
  }
}
