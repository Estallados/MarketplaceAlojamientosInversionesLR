import { Location } from '@angular/common';
import { signal } from '@angular/core';
import { Navigation, Router } from '@angular/router';

import { volverAPaginaAnterior } from './volver';

describe('volverAPaginaAnterior', () => {

  function crearRouter(anterior: boolean) {
    const navegacion = signal<Navigation | null>(
      { previousNavigation: anterior ? ({} as Navigation) : null } as Navigation
    );
    return {
      lastSuccessfulNavigation: navegacion,
      navigateByUrl: vi.fn().mockResolvedValue(true)
    } as unknown as Router & { navigateByUrl: ReturnType<typeof vi.fn> };
  }

  it('vuelve con el historial cuando hubo una página anterior en la aplicación', () => {

    const router = crearRouter(true);
    const location = { back: vi.fn() } as unknown as Location & { back: ReturnType<typeof vi.fn> };

    volverAPaginaAnterior(router, location);

    expect(location.back).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('va a la ruta de respaldo cuando la página se abrió directamente', () => {

    const router = crearRouter(false);
    const location = { back: vi.fn() } as unknown as Location & { back: ReturnType<typeof vi.fn> };

    volverAPaginaAnterior(router, location, '/perfil');

    expect(location.back).not.toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/perfil');
  });
});
