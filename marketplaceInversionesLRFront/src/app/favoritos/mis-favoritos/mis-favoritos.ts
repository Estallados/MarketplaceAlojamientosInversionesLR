import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AlojamientoRespuesta } from '../../alojamientos/models/alojamiento';
import { FavoritoService } from '../services/favorito.service';

/** Favoritos que se piden por página. */
const TAMANIO_PAGINA = 20;

/**
 * Mis favoritos (HU-29): lista los alojamientos guardados por el huésped (CA-170) y permite
 * quitarlos con el corazón (CA-171). Copia la pantalla Favorites del proyecto base.
 *
 * TODO(HU-23, SCRUM-242): cuando exista la pantalla Explorar, agregar en el estado vacío el botón
 * "Explorar alojamientos" del proyecto base (lleva a la búsqueda) y el corazón de favoritos en las
 * tarjetas de Inicio y Explorar, usando FavoritoService.toggle().
 */
@Component({
  selector: 'app-mis-favoritos',
  standalone: true,
  templateUrl: './mis-favoritos.html',
  imports: [CurrencyPipe, DecimalPipe, RouterLink],
})
export class MisFavoritos implements OnInit {
  protected readonly favoritoService = inject(FavoritoService);

  private readonly stays = signal<AlojamientoRespuesta[]>([]);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);
  readonly requiereSesion = signal(false);

  /** Página siguiente por pedir; null si ya se trajeron todas. */
  private readonly siguientePagina = signal<number | null>(null);
  readonly cargandoMas = signal(false);
  readonly hayMas = computed(() => this.siguientePagina() !== null);

  /** Solo se muestran los que siguen guardados: al quitar uno desaparece de la lista (CA-171). */
  readonly favoriteStays = computed(() =>
    this.stays().filter((s) => this.favoritoService.esFavorito(s.idAlojamiento)),
  );

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.errorCarga.set(false);
    this.favoritoService.mensaje.set(null);
    if (!this.favoritoService.disponible()) {
      this.requiereSesion.set(true);
      this.loading.set(false);
      return;
    }
    this.requiereSesion.set(false);
    // Si cambió el usuario, descarta los favoritos del anterior.
    this.favoritoService.cargarSiCorresponde();
    this.loading.set(true);
    this.stays.set([]);
    this.pedirPagina(0);
  }

  cargarMas(): void {
    const pagina = this.siguientePagina();
    if (pagina === null || this.cargandoMas()) {
      return;
    }
    this.cargandoMas.set(true);
    this.pedirPagina(pagina);
  }

  quitar(id: number): void {
    this.favoritoService.toggle(id);
  }

  /** Los dos primeros servicios incluidos, como en el proyecto base. */
  amenidadesResumen(stay: AlojamientoRespuesta, max = 2): string {
    return (stay.amenidades ?? []).slice(0, max).join(' · ') || 'Sin servicios indicados';
  }

  private pedirPagina(pagina: number): void {
    this.favoritoService.listar(pagina, TAMANIO_PAGINA).subscribe({
      next: (respuesta) => {
        const favoritos = respuesta.contenido ?? [];
        this.favoritoService.registrarLista(favoritos);
        const nuevos = favoritos
          .map((f) => f.alojamiento)
          .filter((a): a is AlojamientoRespuesta => a != null);
        this.stays.update((actuales) => [...actuales, ...nuevos]);
        this.siguientePagina.set(respuesta.pagina + 1 < respuesta.totalPaginas ? respuesta.pagina + 1 : null);
        this.loading.set(false);
        this.cargandoMas.set(false);
      },
      error: () => {
        if (this.cargandoMas()) {
          this.cargandoMas.set(false);
          this.favoritoService.mensaje.set('No se pudieron cargar más favoritos. Inténtalo de nuevo.');
          return;
        }
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }
}
