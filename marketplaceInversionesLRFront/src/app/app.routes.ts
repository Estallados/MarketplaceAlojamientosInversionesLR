import { Routes } from '@angular/router';
import { Register } from './register/register';
import { Login } from './login/login';
import { Recover } from './login/recover/recover';
import { Tarifas } from './cotizacion/tarifas/tarifas';
import { roleGuard } from './guards/role.guard';
import { CrearPublicacion } from './alojamientos/crear-publicacion/crear-publicacion';
import { ProfileView } from './pages/guest/profile-view/profile-view';
import { Availability } from './pages/host/availability/availability';

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
  // ====================================

  {
    path: 'host/alojamientos/:id/disponibilidad',
    component: Availability,
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  { path: '**', redirectTo: 'login' }
];
