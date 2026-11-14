import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { SessionService } from '../../services/session.service';
import { NavItem, menuDelRol, rutaDisponible } from '../../navegacion/menus';

/** Grupo de enlaces del pie de página. */
interface GrupoEnlaces {
  titulo: string;
  enlaces: NavItem[];
}

/**
 * Pie de página adaptado del proyecto base (layout/footer). Los enlaces dependen del rol de la
 * sesión y solo aparecen los que ya tienen pantalla en el router, igual que en la barra superior.
 */
@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  template: `
    <footer class="mt-16 border-t border-ink-200 bg-white">
      <div class="mx-auto grid max-w-page gap-8 px-4 py-10 sm:grid-cols-[1.4fr_1fr_1fr] sm:px-6 lg:px-8">
        <div>
          <p class="font-display text-lg font-semibold text-ink-900">
            Inversiones <span class="text-brand-700">LR</span>
          </p>
          <p class="mt-2 max-w-xs text-sm leading-6 text-ink-500">
            Marketplace de alojamientos temporales que conecta anfitriones y huéspedes en Colombia.
          </p>
        </div>

        @for (grupo of grupos(); track grupo.titulo) {
          @if (grupo.enlaces.length) {
            <nav [attr.aria-label]="grupo.titulo">
              <h2 class="text-sm font-semibold text-ink-900">{{ grupo.titulo }}</h2>
              <ul class="mt-3 space-y-2">
                @for (enlace of grupo.enlaces; track enlace[1]) {
                  <li>
                    <a [routerLink]="enlace[1]" class="text-sm text-ink-600 hover:text-brand-700 hover:underline">
                      {{ enlace[0] }}
                    </a>
                  </li>
                }
              </ul>
            </nav>
          }
        }
      </div>
      <p class="border-t border-ink-100 px-4 py-5 text-center text-xs text-ink-500">
        © {{ anio }} Inversiones LR · Proyecto de Ingeniería de Software 1, Universidad El Bosque
      </p>
    </footer>
  `,
})
export class Footer {
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  readonly anio = new Date().getFullYear();

  readonly grupos = computed<GrupoEnlaces[]>(() => {
    const autenticado = this.session.autenticado();
    const cuenta: NavItem[] = autenticado
      ? [
          ['Mi perfil', '/perfil'],
          ['Notificaciones', '/notificaciones'],
        ]
      : [
          ['Iniciar sesión', '/login'],
          ['Crear cuenta', '/registro'],
        ];
    return [
      { titulo: 'Navegación', enlaces: this.disponibles(menuDelRol(autenticado ? this.session.rol() : null)) },
      { titulo: 'Cuenta', enlaces: this.disponibles(cuenta) },
    ];
  });

  private disponibles(enlaces: NavItem[]): NavItem[] {
    return enlaces.filter(([, ruta]) => rutaDisponible(this.router.config, ruta));
  }
}
