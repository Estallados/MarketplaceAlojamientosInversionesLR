import { Component, input } from '@angular/core';

/** Indicador de carga simple y accesible que anuncia el texto a lectores de pantalla. */
@Component({
  selector: 'app-cargando',
  standalone: true,
  template: `
    <div role="status" class="flex flex-col items-center justify-center gap-3 py-24 text-sm text-ink-500">
      <span
        aria-hidden="true"
        class="size-8 animate-spin rounded-full border-2 border-brand-100 border-t-brand-700"
      ></span>
      <span>{{ texto() }}</span>
    </div>
  `,
})
export class Cargando {
  /** Texto que acompaña al indicador. */
  readonly texto = input('Cargando...');
}
