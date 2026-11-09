import { Component, computed, input } from '@angular/core';

import { presentacionEstado } from '../models/estado-publicacion';

// ======================================
// INSIGNIA DEL ESTADO DE LA PUBLICACIÓN
// HU-17, CA-104
//
// Muestra la etiqueta y el color del estado que envió el backend. No
// cambia el valor: `data-valor` conserva el estado tal como llegó y un
// valor desconocido se muestra como "Estado no reconocido".
// ======================================

@Component({
  selector: 'app-insignia-estado',
  standalone: true,
  templateUrl: './insignia-estado.html'
})
export class InsigniaEstado {

  /** Estado recibido del backend. */
  readonly estado = input<string | null | undefined>(null);

  readonly presentacion = computed(
    () => presentacionEstado(this.estado())
  );
}
