import { Component, HostListener, inject, input, output, signal } from '@angular/core';

import { CALIFICACION_MAXIMA, CALIFICACION_MINIMA, MAX_COMENTARIO, Resena } from '../models/resena';
import { ResenaService } from '../services/resena.service';

/**
 * Modal "Calificar estancia" (HU-03), igual al del proyecto base: estrellas de 1 a 5 y comentario
 * opcional. Solo se abre para reservas completadas sin reseña (CA-016); el backend lo vuelve a validar.
 * Al publicar, la reseña queda asociada a la reserva, al alojamiento y al huésped (CA-018).
 */
@Component({
  selector: 'app-calificar-estancia',
  standalone: true,
  templateUrl: './calificar-estancia.html',
})
export class CalificarEstancia {
  private readonly resenas = inject(ResenaService);

  readonly idReserva = input.required<number>();
  readonly tituloAlojamiento = input('');

  readonly publicada = output<Resena>();
  readonly cerrar = output<void>();

  readonly starList = [1, 2, 3, 4, 5];
  readonly MAX_COMENTARIO = MAX_COMENTARIO;

  readonly calificacion = signal(0);
  readonly calificacionHover = signal(0);
  readonly comentario = signal('');
  readonly enviando = signal(false);
  readonly error = signal('');

  @HostListener('document:keydown.escape')
  cancelar(): void {
    if (!this.enviando()) {
      this.cerrar.emit();
    }
  }

  elegirCalificacion(valor: number): void {
    this.calificacion.set(valor);
    this.error.set('');
  }

  enviar(): void {
    if (this.enviando()) {
      return;
    }
    const calificacion = this.calificacion();
    if (calificacion < CALIFICACION_MINIMA || calificacion > CALIFICACION_MAXIMA) {
      this.error.set('Elige una calificación de 1 a 5 estrellas.');
      return;
    }
    const comentario = this.comentario().trim();
    if (comentario.length > MAX_COMENTARIO) {
      this.error.set(`El comentario no puede superar ${MAX_COMENTARIO} caracteres.`);
      return;
    }
    this.enviando.set(true);
    this.error.set('');
    this.resenas
      .crear({ idReserva: this.idReserva(), calificacion, ...(comentario ? { comentario } : {}) })
      .subscribe({
        next: (resena) => {
          this.enviando.set(false);
          this.publicada.emit(resena);
        },
        error: (error: unknown) => {
          this.error.set(this.resenas.mensajeDeError(error, 'No se pudo publicar la reseña.'));
          this.enviando.set(false);
        },
      });
  }
}
