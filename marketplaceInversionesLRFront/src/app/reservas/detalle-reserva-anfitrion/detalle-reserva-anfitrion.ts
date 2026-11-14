import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';

import { fechaDdMmAaaa, monedaCop } from '../models/estado-reserva';
import { Reserva } from '../models/reserva';
import { ReservaService } from '../services/reserva.service';

// ======================================
// DETALLE DE UNA RESERVA PARA EL ANFITRIÓN (HU-25, CA-079)
// GET /reservas/{id}: el backend solo la entrega al anfitrión del
// alojamiento (404 para los demás). Usa el diseño de
// host/reservation-detail del proyecto base, que allí no tenía datos reales.
// ======================================

@Component({
  selector: 'app-detalle-reserva-anfitrion',
  standalone: true,
  templateUrl: './detalle-reserva-anfitrion.html'
})
export class DetalleReservaAnfitrion implements OnInit {

  private readonly route = inject(ActivatedRoute);

  private readonly router = inject(Router);

  private readonly reservaService = inject(ReservaService);

  readonly reserva = signal<Reserva | null>(null);

  readonly loading = signal(true);

  readonly error = signal<string | null>(null);

  private id = 0;

  readonly etiqueta = computed(() => {
    const r = this.reserva();
    if (!r) {
      return '';
    }
    if (r.estado === 'CANCELADA') {
      return 'Cancelada';
    }
    if (r.completada) {
      return 'Finalizada';
    }
    return r.estado === 'CONFIRMADA' ? 'Confirmada' : 'Pendiente';
  });

  readonly badgeClass = computed(() => {
    const s = this.etiqueta();
    const tone =
      s === 'Cancelada'
        ? 'bg-red-50 text-red-700'
        : s === 'Finalizada'
          ? 'bg-ink-100 text-ink-600'
          : s === 'Pendiente'
            ? 'bg-amber-50 text-amber-700'
            : 'bg-brand-50 text-brand-700';
    return 'inline-flex rounded-full px-3 py-1 text-xs font-bold ' + tone;
  });

  readonly summary = computed(() => {
    const r = this.reserva();
    if (!r) {
      return [];
    }
    return [
      { label: 'Código de reserva', value: `#${r.idReserva}` },
      { label: 'Alojamiento', value: r.tituloAlojamiento ?? '—' },
      { label: 'Huésped', value: r.nombreHuesped ?? '—' },
      { label: 'Fecha de entrada', value: fechaDdMmAaaa(r.fechaEntrada) },
      { label: 'Fecha de salida', value: fechaDdMmAaaa(r.fechaSalida) },
      { label: 'Número de huéspedes', value: String(r.huespedes) },
      { label: 'Estado', value: this.etiqueta() }
    ];
  });

  ngOnInit(): void {

    const id = Number(this.route.snapshot.paramMap.get('id'));

    if (!Number.isInteger(id) || id <= 0) {
      this.error.set('El identificador de la reserva no es válido.');
      this.loading.set(false);
      return;
    }

    this.id = id;
    this.cargar();
  }

  cargar(): void {

    this.loading.set(true);
    this.error.set(null);

    this.reservaService.obtener(this.id).subscribe({
      next: reserva => {
        this.reserva.set(reserva);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 404
            ? 'La reserva no existe o no es de uno de tus alojamientos.'
            : 'No se pudo cargar la reserva. Inténtalo de nuevo.'
        );
        this.loading.set(false);
      }
    });
  }

  moneda(valor: number): string {
    return monedaCop(valor);
  }

  back(): void {
    void this.router.navigateByUrl('/host/reservas');
  }
}
