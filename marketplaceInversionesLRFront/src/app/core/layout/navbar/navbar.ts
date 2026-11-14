import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { RolUsuario, SessionService } from '../../services/session.service';
import { CampanaNotificaciones } from '../../../notificaciones/campana/campana-notificaciones';
import { NavItem, ROLE_HOME, VISITOR_ITEMS, menuDelRol, rutaDisponible } from '../../navegacion/menus';

/**
 * Barra de navegación superior, copiada del proyecto base (layout/navbar). Muestra el menú del rol
 * de la sesión, la campana de notificaciones (HU-02), el menú de usuario con su perfil y el cierre de sesión.
 */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CampanaNotificaciones],
  templateUrl: './navbar.html',
  host: {
    class: 'sticky top-0 z-40 block',
  },
})
export class Navbar {
  private readonly router = inject(Router);
  protected readonly session = inject(SessionService);

  roleMenu = false;

  /** URL actual, para saber si se está en el login. */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  readonly estaAutenticado = computed(() => this.session.autenticado());

  readonly role = computed<RolUsuario>(() => this.session.rol() ?? 'Huésped');

  /** El backend solo devuelve el correo: se muestra ese dato sin inventar nombres. */
  readonly user = computed(() => {
    const correo = this.session.correo();
    if (!this.estaAutenticado() || !correo) {
      return { initials: '?', name: 'Invitado', email: '' };
    }
    const nombreVisible = correo.split('@')[0];
    const initials =
      nombreVisible
        .split(/[._\-\s]+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((parte) => parte.charAt(0).toUpperCase())
        .join('') || 'U';
    return { initials, name: nombreVisible, email: correo };
  });

  readonly items = computed<NavItem[]>(() => {
    if (!this.estaAutenticado()) {
      return VISITOR_ITEMS.filter(([, path]) => this.rutaDisponible(path));
    }
    return menuDelRol(this.role()).filter(([, path]) => this.rutaDisponible(path));
  });

  /** Inicio del rol; si su pantalla aún no existe, el perfil (o el login sin sesión). */
  readonly homePath = computed(() => {
    if (!this.estaAutenticado()) {
      return this.rutaDisponible('/home') ? '/home' : '/login';
    }
    const inicio = ROLE_HOME[this.role()];
    return this.rutaDisponible(inicio) ? inicio : '/perfil';
  });

  readonly isLoginPage = computed(() => this.url().split(/[?#]/)[0] === '/login');

  readonly activeClass =
    'text-brand-700 after:absolute after:bottom-0 after:left-4 after:right-4 after:h-0.5 after:bg-brand-700';

  readonly inactiveClass = 'text-ink-500 hover:text-ink-900';

  /** true si alguna ruta del router (sin contar el comodín) atiende esa URL. */
  rutaDisponible(url: string): boolean {
    return rutaDisponible(this.router.config, url);
  }

  cerrarSesion(): void {
    this.session.cerrarSesion();
    this.roleMenu = false;
    void this.router.navigateByUrl('/login');
  }

  closeMenu(): void {
    this.roleMenu = false;
  }

}
