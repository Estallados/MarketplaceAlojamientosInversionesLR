import { Routes } from '@angular/router';
import { Register } from './register/register';
import { Login } from './login/login';
import { Recover } from './login/recover/recover';
import { Tarifas } from './cotizacion/tarifas/tarifas';
import { roleGuard } from './guards/role.guard';

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

  { path: '**', redirectTo: 'login' }
];
