import { InjectionToken } from '@angular/core';

/**
 * Dirección base del backend. Los servicios la inyectan para armar sus rutas, así la URL se cambia
 * en un solo lugar.
 */
export const API_URL = new InjectionToken<string>('API_URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:8081',
});
