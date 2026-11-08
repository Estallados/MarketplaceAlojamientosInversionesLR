import { Routes } from '@angular/router';
import { Register } from './register/register';
import { Login } from './login/login';
import { Recover } from './login/recover/recover';

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

  { path: '**', redirectTo: 'login' }
];
