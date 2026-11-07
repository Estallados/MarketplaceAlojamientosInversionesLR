import { Routes } from '@angular/router';
import { Register } from './register/register';
import { Login } from './login/login';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: Login },
  { path: 'registro', component: Register },
  { path: '**', redirectTo: 'login' }
];
