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

  // ====================================
  // RECUPERACIÓN DE CONTRASEÑA
  // PÚBLICA - SIN INICIAR SESIÓN
  // ====================================

  { path: 'recuperar-contrasena', component: Recover },

  // ====================================
  // RESTABLECER CONTRASEÑA
  // PÚBLICA - SIN INICIAR SESIÓN
  // ====================================

  { path: 'restablecer-contrasena', component: Recover },

  // ====================================
  // PERFIL DE USUARIO (HU-09)
  // CUALQUIER USUARIO CON SESIÓN
  // ====================================

  {
    path: 'perfil',
    component: ProfileView,
    canActivate: [roleGuard]
  },

  // ====================================
  // CONFIGURAR PORCENTAJES DE TARIFAS (HU-24)
  // ANFITRIÓN Y ADMINISTRADOR
  // ====================================

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

  // ====================================
  // CREAR ALOJAMIENTO (HU-15)
  // SOLO ANFITRIÓN
  // ====================================

  {
    path: 'host/alojamientos/crear',
    component: CrearPublicacion,
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  // ====================================
  // PERÍODOS DE DISPONIBILIDAD (HU-20)
  // SOLO ANFITRIÓN
  // Carga diferida para no aumentar el bundle inicial.
  // ====================================

  {
    path: 'host/alojamientos/:id/disponibilidad',
    loadComponent: () =>
      import('./alojamientos/availability/availability')
        .then(m => m.Availability),
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  // ====================================
  // EDITAR PUBLICACIÓN Y SERVICIOS ADICIONALES (HU-16)
  // ANFITRIÓN PROPIETARIO Y ADMINISTRADOR
  // La propiedad la verifica el componente y el backend (403).
  // Carga diferida para no aumentar el bundle inicial.
  // ====================================

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

  // ====================================
  // CONSULTAR DISPONIBILIDAD (HU-21)
  // PÚBLICA: el backend responde 404 si el alojamiento no está ACTIVA
  // y quien consulta no es su anfitrión ni un administrador.
  // Carga diferida para no aumentar el bundle inicial.
  // ====================================

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
