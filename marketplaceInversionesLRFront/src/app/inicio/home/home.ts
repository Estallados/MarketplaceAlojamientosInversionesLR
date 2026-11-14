import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { SessionService } from '../../core/services/session.service';
import { BusquedaService } from '../../busqueda/services/busqueda.service';
import { AlojamientoResultado } from '../../busqueda/models/busqueda';
import { FavoritoService } from '../../favoritos/services/favorito.service';
import { FOTOS, fotoDeReferencia } from './fotos';

/** Cantidad de alojamientos destacados en la página de inicio. */
const DESTACADOS = 4;

/** Alojamientos que se consultan para elegir la foto real de cada tipo. */
const CONSULTADOS = 100;

/** Tipo de alojamiento con su cantidad, tal como lo devuelve GET /alojamientos/tipos. */
export interface TipoConCantidad {
  nombre: string;
  cantidad: number;
  /** Foto de un alojamiento real de ese tipo o, si no hay, una foto de referencia. */
  foto: string;
}

/**
 * Página de inicio pública: barra de búsqueda, alojamientos disponibles, tipos de alojamiento con
 * su cantidad real y acceso para publicar un alojamiento. Adaptada de la página de inicio del
 * proyecto base con los datos y contratos de este proyecto.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CurrencyPipe, DecimalPipe, RouterLink],
  templateUrl: './home.html',
})
export class Home implements OnInit {
  private readonly router = inject(Router);
  protected readonly busquedaService = inject(BusquedaService);
  protected readonly favoritoService = inject(FavoritoService);
  protected readonly session = inject(SessionService);

  // ---- Alojamientos destacados (GET /alojamientos/disponibles) ----
  readonly cargando = signal(true);
  readonly errorCarga = signal(false);
  readonly alojamientos = signal<AlojamientoResultado[]>([]);

  /** Primera foto de un alojamiento real por tipo, para ilustrar la sección de tipos. */
  private readonly fotosPorTipo = signal<Record<string, string>>({});

  readonly fotoPortada = FOTOS.portada;

  // ---- Tipos (GET /alojamientos/tipos) ----
  readonly cargandoTipos = signal(true);
  readonly errorTipos = signal(false);
  private readonly cantidades = signal<{ nombre: string; cantidad: number }[]>([]);

  readonly tipos = computed<TipoConCantidad[]>(() =>
    this.cantidades().map((t) => ({ ...t, foto: this.fotosPorTipo()[t.nombre] ?? fotoDeReferencia(t.nombre) })),
  );

  // ---- Barra de búsqueda: al buscar se abre Explorar con estos filtros ----
  readonly hoy = this.busquedaService.fechaHoy();
  readonly ciudad = signal('');
  readonly entrada = signal('');
  readonly salida = signal('');
  readonly huespedes = signal('');
  readonly errorBusqueda = signal<string | null>(null);

  /** Pregunta antes de cerrar la sesión de un huésped que quiere publicar un alojamiento. */
  readonly confirmarAnfitrion = signal(false);

  /** El administrador no publica alojamientos, así que no ve esa sección. */
  readonly mostrarPublicar = computed(() => !this.session.tieneRol('Administrador'));

  ngOnInit(): void {
    this.favoritoService.cargarSiCorresponde();
    this.cargarAlojamientos();
    this.cargarTipos();
  }

  cargarAlojamientos(): void {
    this.cargando.set(true);
    this.errorCarga.set(false);
    this.busquedaService.disponibles(CONSULTADOS).subscribe({
      next: (lista) => {
        this.alojamientos.set(lista.slice(0, DESTACADOS));
        const fotos: Record<string, string> = {};
        for (const a of lista) {
          const foto = a.imagenes?.at(0);
          if (a.tipo && foto && !fotos[a.tipo]) {
            fotos[a.tipo] = foto;
          }
        }
        this.fotosPorTipo.set(fotos);
        this.cargando.set(false);
      },
      error: () => {
        this.alojamientos.set([]);
        this.errorCarga.set(true);
        this.cargando.set(false);
      },
    });
  }

  /** Ordena los tipos de mayor a menor cantidad (y por nombre si empatan); omite los vacíos. */
  cargarTipos(): void {
    this.cargandoTipos.set(true);
    this.errorTipos.set(false);
    this.busquedaService.cantidadPorTipo().subscribe({
      next: (datos) => {
        this.cantidades.set(
          Object.entries(datos ?? {})
            .map(([nombre, cantidad]) => ({ nombre, cantidad: Number(cantidad) }))
            .filter((t) => t.nombre.trim() && t.cantidad > 0)
            .sort((a, b) => b.cantidad - a.cantidad || a.nombre.localeCompare(b.nombre, 'es')),
        );
        this.cargandoTipos.set(false);
      },
      error: () => {
        this.cantidades.set([]);
        this.errorTipos.set(true);
        this.cargandoTipos.set(false);
      },
    });
  }

  /** Valida la barra y abre Explorar con los filtros en la URL. */
  buscar(): void {
    const campos = { ciudad: this.ciudad(), entrada: this.entrada(), salida: this.salida(), huespedes: this.huespedes() };
    const error = this.busquedaService.validarBusqueda(campos);
    this.errorBusqueda.set(error);
    if (error) {
      return;
    }
    void this.router.navigate(['/explorar'], { queryParams: this.busquedaService.paramsBusqueda(campos) });
  }

  /** "Publicar alojamiento": el anfitrión va a sus alojamientos; el visitante, al registro como anfitrión. */
  publicar(): void {
    if (this.session.tieneRol('Anfitrión')) {
      void this.router.navigateByUrl('/host/alojamientos');
    } else if (this.session.tieneRol('Huésped')) {
      this.confirmarAnfitrion.set(true);
    } else {
      this.irARegistroAnfitrion();
    }
  }

  @HostListener('document:keydown.escape')
  cancelarCambio(): void {
    this.confirmarAnfitrion.set(false);
  }

  /** Cierra la sesión de huésped y lleva al registro como anfitrión. */
  aceptarCambio(): void {
    this.confirmarAnfitrion.set(false);
    this.session.cerrarSesion();
    this.irARegistroAnfitrion();
  }

  private irARegistroAnfitrion(): void {
    void this.router.navigate(['/registro'], { queryParams: { rol: 'anfitrion' } });
  }
}
