import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { EMPTY, expand, reduce } from 'rxjs';

import { AlojamientoRespuesta } from '../../alojamientos/models/alojamiento';
import { PublicacionHostService } from '../../alojamientos/services/publicacion-host.service';
import { Resena, ResumenResenas } from '../models/resena';
import { ResenaService } from '../services/resena.service';
import { Cargando } from '../../core/ui/cargando/cargando';

/** Reseñas por página. */
const TAMANIO = 10;

/**
 * Reseñas de mis alojamientos (HU-03, anfitrión): promedio, cantidad y listado de las reseñas
 * publicadas, filtrable por alojamiento. Con un alojamiento elegido solo se muestran las suyas (CA-019).
 */
@Component({
  selector: 'app-resenas-anfitrion',
  imports: [Cargando],
  standalone: true,
  templateUrl: './resenas-anfitrion.html',
})
export class ResenasAnfitrion implements OnInit {
  private readonly resenas = inject(ResenaService);
  private readonly publicaciones = inject(PublicacionHostService);

  readonly starList = [1, 2, 3, 4, 5];
  readonly tamanio = TAMANIO;

  readonly reviews = signal<Resena[]>([]);
  readonly accommodations = signal<AlojamientoRespuesta[]>([]);
  readonly filter = signal('Todos');
  readonly pagina = signal(0);
  readonly totalPaginas = signal(0);
  readonly resumen = signal<ResumenResenas | null>(null);
  readonly cargando = signal(true);
  readonly cargandoAlojamientos = signal(true);
  readonly error = signal('');
  readonly errorAlojamientos = signal('');

  readonly cantidad = computed(() => this.resumen()?.cantidad ?? 0);
  readonly average = computed(() => {
    const promedio = this.resumen()?.promedio;
    return promedio == null ? '—' : promedio.toFixed(1).replace('.', ',');
  });
  readonly inicioPagina = computed(() => (this.cantidad() === 0 ? 0 : this.pagina() * TAMANIO + 1));
  readonly finPagina = computed(() => Math.min((this.pagina() + 1) * TAMANIO, this.cantidad()));
  readonly nombreFiltro = computed(() => {
    if (this.filter() === 'Todos') {
      return 'Todos mis alojamientos';
    }
    const id = Number(this.filter());
    return this.accommodations().find((a) => a.idAlojamiento === id)?.titulo ?? `Alojamiento #${id}`;
  });

  ngOnInit(): void {
    this.cargarAlojamientos();
    this.cargarResenas();
  }

  cargarAlojamientos(): void {
    this.cargandoAlojamientos.set(true);
    this.errorAlojamientos.set('');
    this.publicaciones
      .obtenerMisAlojamientos(0, 100)
      .pipe(
        expand((r) =>
          r.pagina + 1 < r.totalPaginas ? this.publicaciones.obtenerMisAlojamientos(r.pagina + 1, 100) : EMPTY,
        ),
        reduce((todos, r) => [...todos, ...(r.contenido ?? [])], [] as AlojamientoRespuesta[]),
      )
      .subscribe({
        next: (lista) => {
          this.accommodations.set([...lista].sort((a, b) => a.titulo.localeCompare(b.titulo, 'es')));
          this.cargandoAlojamientos.set(false);
        },
        error: (error: unknown) => {
          this.cargandoAlojamientos.set(false);
          this.errorAlojamientos.set(this.resenas.mensajeDeError(error, 'No fue posible consultar tus alojamientos.'));
        },
      });
  }

  cargarResenas(): void {
    this.cargando.set(true);
    this.error.set('');
    const id = this.filter() === 'Todos' ? undefined : Number(this.filter());
    this.resenas.deAnfitrion(this.pagina(), TAMANIO, id).subscribe({
      next: (respuesta) => {
        this.resumen.set(respuesta);
        this.reviews.set(respuesta?.resenas?.contenido ?? []);
        this.totalPaginas.set(respuesta?.resenas?.totalPaginas ?? 0);
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        this.resumen.set(null);
        this.reviews.set([]);
        this.totalPaginas.set(0);
        this.cargando.set(false);
        this.error.set(this.resenas.mensajeDeError(error, 'No fue posible cargar las reseñas.'));
      },
    });
  }

  cambiarFiltro(valor: string): void {
    if (valor === this.filter()) {
      return;
    }
    this.filter.set(valor);
    this.pagina.set(0);
    this.cargarResenas();
  }

  cambiarPagina(nueva: number): void {
    if (this.cargando() || nueva < 0 || nueva >= this.totalPaginas() || nueva === this.pagina()) {
      return;
    }
    this.pagina.set(nueva);
    this.cargarResenas();
  }

  actualizar(): void {
    this.cargarResenas();
    this.cargarAlojamientos();
  }

  /** "2026-11-10T09:00:00" → "10/11/2026". */
  fechaFormateada(valor: string | null | undefined): string {
    if (!valor) {
      return 'Fecha no disponible';
    }
    const partes = /^(\d{4})-(\d{2})-(\d{2})/.exec(valor);
    return partes ? `${partes[3]}/${partes[2]}/${partes[1]}` : valor;
  }
}
