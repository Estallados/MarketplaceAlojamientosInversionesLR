import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { Resena } from '../models/resena';
import { ResenaService } from '../services/resena.service';

/**
 * Mis reseñas del huésped (HU-03): las calificaciones que dejó después de sus estancias, con el
 * alojamiento y la fecha (CA-018). Las ocultas por moderación siguen visibles para su autor, con una marca.
 */
@Component({
  selector: 'app-mis-resenas',
  standalone: true,
  templateUrl: './mis-resenas.html',
  imports: [DatePipe],
})
export class MisResenas implements OnInit {
  private readonly router = inject(Router);
  private readonly resenaService = inject(ResenaService);

  // ==================== DATOS ====================
  readonly reviews = signal<Resena[]>([]);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);

  readonly starList = [1, 2, 3, 4, 5];

  // ==================== INICIALIZACIÓN ====================
  ngOnInit(): void {
    this.cargarResenas();
  }

  reintentar(): void {
    this.cargarResenas();
  }

  private cargarResenas(): void {
    this.loading.set(true);
    this.errorCarga.set(false);
    this.resenaService.mias().subscribe({
      next: (respuesta) => {
        this.reviews.set(respuesta ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.reviews.set([]);
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }

  // ==================== PRESENTACIÓN ====================
  /** Las reseñas ocultas por un administrador siguen siendo visibles para su autor, con una marca. */
  estaOculta(resena: Resena): boolean {
    return resena.estado === 'OCULTA';
  }

  tituloAlojamiento(resena: Resena): string {
    return resena.tituloAlojamiento ?? '';
  }

  irAReservas(): void {
    void this.router.navigateByUrl('/reservas');
  }
}
