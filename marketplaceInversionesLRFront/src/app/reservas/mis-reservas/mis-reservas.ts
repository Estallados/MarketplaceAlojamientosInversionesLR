import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import {
  PESTANAS_RESERVA,
  PestanaReserva,
  categoriaReserva,
  claseEtiquetaReserva,
  etiquetaReserva,
  fechaDdMmAaaa,
  monedaCop
} from '../models/estado-reserva';
import { Reserva } from '../models/reserva';
import { ReservaService } from '../services/reserva.service';

// ======================================
// MIS RESERVAS (HU-25, CA-078)
// GET /reservas/mias: todas las reservas del huésped de la sesión con su
// estado actual, agrupadas en pestañas como en el proyecto base.
// ======================================

@Component({
  selector: 'app-mis-reservas',
  standalone: true,
  templateUrl: './mis-reservas.html'
})
export class MisReservas implements OnInit {

  private readonly router = inject(Router);

  private readonly reservaService = inject(ReservaService);

  readonly reservas = signal<Reserva[]>([]);

  readonly loading = signal(true);

  readonly errorCarga = signal(false);

  readonly pestanaActiva = signal<PestanaReserva>('Próximas');

  readonly grupos = computed<Record<PestanaReserva, Reserva[]>>(() => {
    const grupos: Record<PestanaReserva, Reserva[]> = { Próximas: [], Activas: [], Finalizadas: [], Canceladas: [] };
    for (const r of this.reservas()) {
      grupos[categoriaReserva(r)].push(r);
    }
    return grupos;
  });

  readonly pestanas = computed(() =>
    PESTANAS_RESERVA.map(value => ({ value, label: `${value} ${this.grupos()[value].length}` }))
  );

  readonly actuales = computed(() => this.grupos()[this.pestanaActiva()]);

  ngOnInit(): void {
    this.cargarReservas();
  }

  reintentar(): void {
    this.cargarReservas();
  }

  private cargarReservas(): void {

    this.loading.set(true);
    this.errorCarga.set(false);

    this.reservaService.todasMisReservas().subscribe({
      next: reservas => {
        this.reservas.set(reservas);
        this.loading.set(false);
      },
      error: () => {
        this.reservas.set([]);
        this.errorCarga.set(true);
        this.loading.set(false);
      }
    });
  }

  // ==================== PRESENTACIÓN ====================

  claseTab(tab: PestanaReserva): string {
    return (
      'px-5 pb-4 text-sm font-bold ' +
      (this.pestanaActiva() === tab ? 'border-b-2 border-emerald-700 text-emerald-700' : 'text-slate-500')
    );
  }

  etiqueta(r: Reserva): string {
    return etiquetaReserva(r);
  }

  claseEtiqueta(r: Reserva): string {
    return claseEtiquetaReserva(this.etiqueta(r));
  }

  fecha(valor: string | null | undefined): string {
    return fechaDdMmAaaa(valor);
  }

  moneda(valor: number | null | undefined): string {
    return monedaCop(valor);
  }

  // ==================== NAVEGACIÓN ====================

  abrir(id: number): void {
    void this.router.navigate(['/reservas', id]);
  }
}
