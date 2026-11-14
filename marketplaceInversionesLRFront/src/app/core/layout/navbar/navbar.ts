import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Route, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { RolUsuario, SessionService } from '../../services/session.service';
import { CampanaNotificaciones } from '../../../notificaciones/campana/campana-notificaciones';

type NavItem = [label: string, path: string];

// ======================================
// MENÚS SEGÚN ROL (los mismos del proyecto base)
// Solo se muestran los que ya tienen pantalla en el router: los demás aparecen solos
// cuando su historia agregue la ruta, sin tocar esta barra.
// ======================================

const GUEST_ITEMS: NavItem[] = [
  ['Inicio', '/home'],
  ['Explorar', '/explorar'],
  ['Mis favoritos', '/favoritos'],
  ['Mensajes', '/mensajes'],
  ['Mis reservas', '/reservas'],
  ['Mis reseñas', '/resenas'],
];

const HOST_ITEMS: NavItem[] = [
  ['Resumen', '/host/dashboard'],
  ['Mis alojamientos', '/host/alojamientos'],
  ['Reservas', '/host/reservas'],
  ['Chat', '/host/chat'],
  ['Reseñas', '/host/resenas'],
];

const ADMIN_ITEMS: NavItem[] = [
  ['Dashboard', '/admin/dashboard'],
  ['Usuarios', '/admin/usuarios'],
  ['Alojamientos', '/admin/alojamientos'],
  ['Reservas', '/admin/reservas'],
  ['Reseñas', '/admin/resenas'],
  ['Reportes', '/admin/reportes'],
  ['Auditoría', '/admin/auditoria'],
];

const VISITOR_ITEMS: NavItem[] = [
  ['Inicio', '/home'],
  ['Explorar', '/explorar'],
];

/** Página de inicio de cada rol. */
const ROLE_HOME: Record<RolUsuario, string> = {
  Huésped: '/home',
  Anfitrión: '/host/dashboard',
  Administrador: '/admin/dashboard',
};

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
    const lista =
      this.role() === 'Anfitrión' ? HOST_ITEMS : this.role() === 'Administrador' ? ADMIN_ITEMS : GUEST_ITEMS;
    return lista.filter(([, path]) => this.rutaDisponible(path));
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
    const partes = url.split(/[?#]/)[0].split('/').filter(Boolean);
    return this.router.config.some((ruta) => this.coincide(ruta, partes));
  }

  cerrarSesion(): void {
    this.session.cerrarSesion();
    this.roleMenu = false;
    void this.router.navigateByUrl('/login');
  }

  closeMenu(): void {
    this.roleMenu = false;
  }

  private coincide(ruta: Route, partes: string[]): boolean {
    if (ruta.path === undefined || ruta.path === '**' || ruta.redirectTo !== undefined) {
      return false;
    }
    const patron = ruta.path.split('/').filter(Boolean);
    return (
      patron.length === partes.length &&
      patron.every((segmento, i) => segmento.startsWith(':') || segmento === partes[i])
    );
  }
}
