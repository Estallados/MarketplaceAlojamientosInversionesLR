import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Cotizacion } from '../../cotizacion/models/cotizacion';
import {
  claseEtiquetaReserva,
  etiquetaReserva,
  fechaCorta,
  monedaCop
} from '../models/estado-reserva';
import { Reserva } from '../models/reserva';
import { ReservaService } from '../services/reserva.service';

/** Línea del resumen de la cotización. */
export interface LineaDesglose {
  label: string;
  value: string;
}

// ======================================
// DETALLE DE UNA RESERVA DEL HUÉSPED (HU-25, CA-078)
// GET /reservas/{id}: estado actual, fechas, huéspedes, alojamiento,
// política y el desglose congelado de la cotización. Copia la pantalla
// guest/reservation-detail del proyecto base; cancelar (HU-01), reseñar
// (HU-03) y contactar al anfitrión (HU-28) quedan para sus historias.
// ======================================

@Component({
  selector: 'app-detalle-reserva',
  standalone: true,
  templateUrl: './detalle-reserva.html'
})
export class DetalleReserva implements OnInit {

  private readonly router = inject(Router);

  private readonly route = inject(ActivatedRoute);

  private readonly reservaService = inject(ReservaService);

  readonly reserva = signal<Reserva | null>(null);

  readonly loading = signal(true);

  readonly errorCarga = signal(false);

  readonly mensajeError = signal('');

  private idReserva = 0;

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
    this.cargarReserva();
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

  private cargarReserva(): void {

    this.loading.set(true);
    this.errorCarga.set(false);

    this.reservaService.obtener(this.idReserva).subscribe({
      next: reserva => {
        this.reserva.set(reserva);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
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
