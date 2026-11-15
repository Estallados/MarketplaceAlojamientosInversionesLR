import { InjectionToken } from '@angular/core';

/** Configuración que `config.js` deja en `window` antes de que arranque Angular. */
interface ConfiguracionGlobal {
  __config?: { apiUrl?: string };
}

/** Dirección del backend cuando `config.js` no define otra (desarrollo local). */
export const API_URL_LOCAL = 'http://localhost:8081';

/**
 * Lee la dirección del backend de `config.js`. En el contenedor ese archivo se genera al arrancar
 * con la variable `API_URL`, así la misma imagen sirve para cualquier ambiente sin recompilar.
 */
export function leerApiUrl(global: ConfiguracionGlobal = globalThis as ConfiguracionGlobal): string {
  const url = global.__config?.apiUrl?.trim();
  return url ? url.replace(/\/+$/, '') : API_URL_LOCAL;
}

/**
 * Dirección base del backend. Los servicios la inyectan para armar sus rutas, así la URL se cambia
 * en un solo lugar.
 */
export const API_URL = new InjectionToken<string>('API_URL', {
  providedIn: 'root',
  factory: () => leerApiUrl(),
});
