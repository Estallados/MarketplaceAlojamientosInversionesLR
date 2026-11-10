import { Location } from '@angular/common';
import { Router } from '@angular/router';

/**
 * Vuelve a la página anterior de la aplicación. Si la página se abrió
 * directamente (sin una navegación previa dentro de la app), va a la ruta de
 * respaldo para no salir del sitio.
 *
 * @param router router de la aplicación
 * @param location historial del navegador
 * @param respaldo ruta a la que ir si no hay página anterior
 */
export function volverAPaginaAnterior(
  router: Router,
  location: Location,
  respaldo = '/'
): void {

  if (router.lastSuccessfulNavigation()?.previousNavigation) {
    location.back();
    return;
  }

  void router.navigateByUrl(respaldo);
}
