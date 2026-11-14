import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';

import { Dashboard } from '../models/dashboard';
import { AdminDashboardService } from '../services/admin-dashboard.service';

/** Tarjeta de indicador. */
export interface DashboardMetric {
  label: string;
  value: string;
  icon: 'users' | 'building' | 'chart' | 'money' | 'shield';
  /** Dato secundario opcional que se muestra como etiqueta. */
  delta?: string;
}

export interface ReservationStat {
  label: 'Confirmadas' | 'Pendientes' | 'Finalizadas' | 'Canceladas';
  value: number;
  percent: number;
}

export interface CityDemand {
  city: string;
  total: number;
}

/**
 * Dashboard administrativo (HU-04): tarjetas de indicadores, reservas por estado y ciudades con
 * mayor demanda, con el diseño del proyecto base. Los datos vienen ya calculados de GET /admin/dashboard (CA-022).
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  templateUrl: './dashboard.html',
})
export class DashboardAdmin implements OnInit {
  private readonly admin = inject(AdminDashboardService);

  readonly loading = signal(true);
  readonly errorCarga = signal<string | null>(null);
  readonly metrics = signal<DashboardMetric[]>([]);
  readonly totalReservations = signal(0);
  readonly reservationStats = signal<ReservationStat[]>([]);
  readonly topCities = signal<CityDemand[]>([]);

  /** Colores de barra por estado (clases completas para que Tailwind las detecte). */
  readonly barColor: Record<ReservationStat['label'], string> = {
    Confirmadas: 'bg-emerald-600',
    Pendientes: 'bg-amber-400',
    Finalizadas: 'bg-sky-500',
    Canceladas: 'bg-red-400',
  };

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    this.errorCarga.set(null);
    this.admin.obtener(5).subscribe({
      next: (d) => {
        this.aplicar(d);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorCarga.set(
          error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)
            ? 'No tienes permisos para consultar el dashboard.'
            : error instanceof HttpErrorResponse && error.status === 0
              ? 'No hay conexión con el servidor.'
              : 'No se pudieron cargar los datos del dashboard.',
        );
        this.loading.set(false);
      },
    });
  }

  private aplicar(d: Dashboard): void {
    const n = (valor: number | null | undefined) => (valor ?? 0).toLocaleString('es-CO');
    const alojamientos = d.alojamientos?.porEstado ?? {};
    const reservas = d.reservas?.porEstado ?? {};
    const finalizadas = d.reservas?.confirmadasFinalizadas ?? 0;
    const conteo: Record<ReservationStat['label'], number> = {
      Confirmadas: Math.max(0, (reservas['CONFIRMADA'] ?? 0) - finalizadas),
      Pendientes: (reservas['PENDIENTE_PAGO'] ?? 0) + (reservas['EN_REVISION'] ?? 0),
      Finalizadas: finalizadas,
      Canceladas: reservas['CANCELADA'] ?? 0,
    };
    const totalReservas = d.reservas?.total ?? 0;
    const porRevisar = alojamientos['PENDIENTE_REVISION'] ?? 0;

    this.metrics.set([
      { label: 'Usuarios registrados', value: n(d.totalUsuarios), icon: 'users' },
      {
        label: 'Alojamientos',
        value: n(d.alojamientos?.total),
        icon: 'building',
        delta: `${n(alojamientos['ACTIVA'])} activos`,
      },
      { label: 'Reservas', value: n(totalReservas), icon: 'chart', delta: `${n(conteo.Confirmadas)} confirmadas` },
      { label: 'Ingresos estimados', value: this.moneda(d.ingresosEstimados), icon: 'money', delta: 'COP' },
      {
        label: 'Alojamientos por revisar',
        value: n(porRevisar),
        icon: 'shield',
        delta: porRevisar > 0 ? 'Requieren acción' : 'Al día',
      },
    ]);
    this.totalReservations.set(totalReservas);
    const base = totalReservas || 1;
    this.reservationStats.set(
      (Object.keys(conteo) as ReservationStat['label'][]).map((label) => ({
        label,
        value: conteo[label],
        percent: Math.round((conteo[label] / base) * 100),
      })),
    );
    this.topCities.set((d.ciudadesTop ?? []).map((c) => ({ city: c.ciudad, total: c.totalReservas ?? 0 })));
  }

  private moneda(valor: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(
      Number(valor ?? 0),
    );
  }
}
