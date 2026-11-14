import { Route, Routes } from '@angular/router';

import { RolUsuario } from '../services/session.service';

/** Enlace de navegación: texto visible y ruta. */
export type NavItem = [label: string, path: string];

// ======================================
// MENÚS SEGÚN ROL (los mismos del proyecto base)
// Solo se muestran los que ya tienen pantalla en el router: los demás aparecen solos
// cuando su historia agregue la ruta, sin tocar la barra ni el pie de página.
// ======================================

export const GUEST_ITEMS: NavItem[] = [
  ['Inicio', '/home'],
  ['Explorar', '/explorar'],
  ['Mis favoritos', '/favoritos'],
  ['Mensajes', '/mensajes'],
  ['Mis reservas', '/reservas'],
  ['Mis reseñas', '/resenas'],
];

export const HOST_ITEMS: NavItem[] = [
  ['Resumen', '/host/dashboard'],
  ['Mis alojamientos', '/host/alojamientos'],
  ['Reservas', '/host/reservas'],
  ['Chat', '/host/chat'],
  ['Reseñas', '/host/resenas'],
];

export const ADMIN_ITEMS: NavItem[] = [
  ['Dashboard', '/admin/dashboard'],
  ['Usuarios', '/admin/usuarios'],
  ['Alojamientos', '/admin/alojamientos'],
  ['Reservas', '/admin/reservas'],
  ['Reseñas', '/admin/resenas'],
  ['Reportes', '/admin/reportes'],
  ['Auditoría', '/admin/auditoria'],
];

export const VISITOR_ITEMS: NavItem[] = [
  ['Inicio', '/home'],
  ['Explorar', '/explorar'],
];

/** Página de inicio de cada rol. */
export const ROLE_HOME: Record<RolUsuario, string> = {
  Huésped: '/home',
  Anfitrión: '/host/dashboard',
  Administrador: '/admin/dashboard',
};

/** Menú del rol de la sesión (o del visitante sin sesión). */
export function menuDelRol(rol: RolUsuario | null): NavItem[] {
  if (rol === null) {
    return VISITOR_ITEMS;
  }
  return rol === 'Anfitrión' ? HOST_ITEMS : rol === 'Administrador' ? ADMIN_ITEMS : GUEST_ITEMS;
}

/** true si alguna ruta del router (sin contar el comodín ni las redirecciones) atiende esa URL. */
export function rutaDisponible(config: Routes, url: string): boolean {
  const partes = url.split(/[?#]/)[0].split('/').filter(Boolean);
  return config.some((ruta) => coincide(ruta, partes));
}

function coincide(ruta: Route, partes: string[]): boolean {
  if (ruta.path === undefined || ruta.path === '**' || ruta.redirectTo !== undefined) {
    return false;
  }
  const patron = ruta.path.split('/').filter(Boolean);
  return (
    patron.length === partes.length &&
    patron.every((segmento, i) => segmento.startsWith(':') || segmento === partes[i])
  );
}
