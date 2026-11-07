import { Routes } from '@angular/router';


import { Register } from './register/register';


export const routes: Routes = [


  {
    path: '',
    redirectTo: 'registro',
    pathMatch: 'full'
  },


  {
    path: 'registro',
    component: Register
  },


  {
    path: '**',
    redirectTo: 'registro'
  }

];
