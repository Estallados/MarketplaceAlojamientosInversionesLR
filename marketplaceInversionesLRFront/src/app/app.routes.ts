import { Routes } from '@angular/router';
import { Register } from './auth/register/register';
import { Login } from './auth/login/login';
import { Recover } from './auth/recover/recover';
import { Tarifas } from './cotizacion/tarifas/tarifas';
import { roleGuard } from './core/guards/role.guard';
import { CrearPublicacion } from './alojamientos/crear-publicacion/crear-publicacion';
import { ProfileView } from './perfil/profile-view/profile-view';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: Login },
  { path: 'registro', component: Register },



  { path: 'recuperar-contrasena', component: Recover },



  { path: 'restablecer-contrasena', component: Recover },



  {
    path: 'perfil',
    component: ProfileView,
    canActivate: [roleGuard]
  },



  {
    path: 'host/tarifas',
    component: Tarifas,
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  {
    path: 'admin/tarifas',
    component: Tarifas,
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },



  {
    path: 'host/alojamientos/crear',
    component: CrearPublicacion,
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },



  {
    path: 'host/alojamientos/:id/disponibilidad',
    loadComponent: () =>
      import('./alojamientos/availability/availability')
        .then(m => m.Availability),
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },



  {
    path: 'host/alojamientos/:id/editar',
    loadComponent: () =>
      import('./alojamientos/editar-publicacion/editar-publicacion')
        .then(m => m.EditarPublicacion),
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  {
    path: 'admin/alojamientos/:id/editar',
    loadComponent: () =>
      import('./alojamientos/editar-publicacion/editar-publicacion')
        .then(m => m.EditarPublicacion),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },



  {
    path: 'admin/alojamientos',
    loadComponent: () =>
      import('./administracion/revisar-publicaciones/revisar-publicaciones')
        .then(m => m.RevisarPublicaciones),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },

  {
    path: 'admin/alojamientos/:id',
    loadComponent: () =>
      import('./administracion/detalle-publicacion/detalle-publicacion')
        .then(m => m.DetallePublicacion),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },



  {
    path: 'alojamientos/:id/consultar-disponibilidad',
    loadComponent: () =>
      import('./disponibilidad/consultar-disponibilidad/consultar-disponibilidad')
        .then(m => m.ConsultarDisponibilidad)
  },

  // ====================================
  // CREAR RESERVA CON BLOQUEO AUTOMÁTICO DE FECHAS (HU-22)
  // SOLO HUÉSPED: POST /reservas exige el rol HUESPED.
  // Recibe ?llegada=&salida= (y opcional &huespedes=) desde HU-21.
  // Carga diferida para no aumentar el bundle inicial.
  // ====================================

  {
    path: 'alojamientos/:id/reservar',
    loadComponent: () =>
      import('./reservas/confirmar-reserva/confirmar-reserva')
        .then(m => m.ConfirmarReserva),
    canActivate: [roleGuard],
    data: { roles: ['Huésped'] }
  },

  { path: '**', redirectTo: 'login' }
];
