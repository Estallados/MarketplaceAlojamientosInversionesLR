import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, interval, merge, startWith } from 'rxjs';

import { SessionService } from '../../core/services/session.service';
import { NotificacionesService } from '../services/notificaciones.service';

/** Cada cuánto se vuelve a consultar el contador (ms). */
const INTERVALO = 60_000;

/**
 * Campana de la barra superior con el contador de notificaciones sin leer (HU-02).
 * Se actualiza al cargar, al navegar y cada minuto.
 */
@Component({
  selector: 'app-campana-notificaciones',
  standalone: true,
  imports: [RouterLink],
  template: `
    <a
      routerLink="/notificaciones"
      [attr.aria-label]="contador() > 0 ? 'Notificaciones, ' + contador() + ' sin leer' : 'Notificaciones'"
      class="relative grid size-11 place-items-center rounded-full text-ink-600 transition hover:bg-ink-100"
    >
      <svg
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
      </svg>
      @if (contador() > 0) {
        <span
          class="absolute right-1.5 top-1.5 grid h-5 min-w-5 place-items-center rounded-full bg-brand-700 px-1 text-xs font-bold text-white"
        >
          {{ contador() > 99 ? '99+' : contador() }}
        </span>
      }
    </a>
  `,
})
export class CampanaNotificaciones {
  private readonly notificaciones = inject(NotificacionesService);
  private readonly session = inject(SessionService);

  readonly contador = this.notificaciones.sinLeer;

  constructor() {
    merge(inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd)), interval(INTERVALO))
      .pipe(startWith(null), takeUntilDestroyed())
      .subscribe(() => this.actualizar());
  }

  actualizar(): void {
    if (this.session.autenticado()) {
      this.notificaciones.actualizarContador();
    } else {
      this.notificaciones.sinLeer.set(0);
    }
  }
}
