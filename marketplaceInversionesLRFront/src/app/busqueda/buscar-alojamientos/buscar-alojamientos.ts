import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';

import { AlojamientoResultado, CamposBusqueda } from '../models/busqueda';
import { BusquedaService } from '../services/busqueda.service';

type SortOption = 'Recomendados' | 'Precio menor' | 'Precio mayor' | 'Mejor calificados';

/** Foto de ejemplo cuando el alojamiento no tiene imágenes. */

/** Alojamientos por página en los resultados. */
const POR_PAGINA = 9;

/**
 * Buscar alojamientos con filtros (HU-23): ciudad, fechas, huéspedes, tipo, precio y servicios,
 * con listado paginado y estados de carga, vacío y error.
 */
@Component({
  selector: 'app-buscar-alojamientos',
  standalone: true,
  templateUrl: './buscar-alojamientos.html',
  imports: [CurrencyPipe, DecimalPipe],
})
export class BuscarAlojamientos implements OnInit {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly busquedaService = inject(BusquedaService);


  readonly stays = signal<AlojamientoResultado[]>([]);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);


  readonly filterType = signal('Todos');
  readonly sortBy = signal<SortOption>('Recomendados');
  readonly precioMin = signal(0);
  readonly precioMax = signal(0);
  readonly serviciosSeleccionados = signal<string[]>([]);

  readonly sortOptions: SortOption[] = ['Recomendados', 'Precio menor', 'Precio mayor', 'Mejor calificados'];


  readonly hoy = this.busquedaService.fechaHoy();
  readonly ciudad = signal('');
  readonly entrada = signal('');
  readonly salida = signal('');
  readonly huespedes = signal('');
  readonly errorBusqueda = signal<string | null>(null);
  /** true cuando los resultados vienen de una búsqueda (y no del catálogo completo). */
  readonly busquedaAplicada = signal(false);


  readonly pagina = signal(0);


  readonly preciosDisponibles = computed(() =>
    this.stays()
      .map((a) => a.precioNoche)
      .filter((p): p is number => typeof p === 'number' && Number.isFinite(p) && p >= 0),
  );
  readonly precioMinimoDisponible = computed(() => {
    const precios = this.preciosDisponibles();
    return precios.length > 0 ? Math.min(...precios) : 0;
  });
  readonly precioMaximoDisponible = computed(() => {
    const precios = this.preciosDisponibles();
    return precios.length > 0 ? Math.max(...precios) : 0;
  });
  /** Paso del slider: ~100 posiciones, redondeado a 1, 10, 100, 1.000... */
  readonly paso = computed(() => {
    const rango = this.precioMaximoDisponible() - this.precioMinimoDisponible();
    if (rango <= 0) {
      return 1;
    }
    return Math.max(1, Math.pow(10, Math.floor(Math.log10(rango / 100))));
  });
  readonly sinRangoDePrecios = computed(() => this.precioMaximoDisponible() <= this.precioMinimoDisponible());
  readonly porcentajeMin = computed(() => this.aPorcentaje(this.precioMin()));
  readonly porcentajeMax = computed(() => this.aPorcentaje(this.precioMax()));


  readonly types = computed(() => {
    const tipos = [
      ...new Set(
        this.stays()
          .map((a) => a.tipo)
          .filter((t): t is string => typeof t === 'string' && t.trim().length > 0),
      ),
    ].sort((a, b) => a.localeCompare(b, 'es'));
    return ['Todos', ...tipos];
  });

  readonly services = computed(() =>
    [
      ...new Set(
        this.stays()
          .flatMap((a) => a.amenidades ?? [])
          .filter((s): s is string => typeof s === 'string' && s.trim().length > 0)
          .map((s) => s.trim()),
      ),
    ].sort((a, b) => a.localeCompare(b, 'es')),
  );


  readonly visible = computed(() => {
    let resultado = [...this.stays()];

    const tipo = this.filterType();
    if (tipo !== 'Todos') {
      resultado = resultado.filter((a) => a.tipo === tipo);
    }

    const minimo = this.precioMin();
    const maximo = this.precioMax();
    resultado = resultado.filter((a) => {
      const precio = a.precioNoche;
      return typeof precio === 'number' && Number.isFinite(precio) && precio >= minimo && precio <= maximo;
    });

    resultado = resultado.filter((a) => this.tieneServiciosSeleccionados(a));

    switch (this.sortBy()) {
      case 'Precio menor':
        resultado.sort((a, b) => (a.precioNoche ?? 0) - (b.precioNoche ?? 0));
        break;
      case 'Precio mayor':
        resultado.sort((a, b) => (b.precioNoche ?? 0) - (a.precioNoche ?? 0));
        break;
      case 'Mejor calificados':
        resultado.sort((a, b) => (b.promedioCalificacion ?? 0) - (a.promedioCalificacion ?? 0));
        break;
      default:
        break;
    }
    return resultado;
  });

  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.visible().length / POR_PAGINA)));
  readonly paginaActual = computed(() => Math.min(this.pagina(), this.totalPaginas() - 1));
  readonly paginados = computed(() => {
    const inicio = this.paginaActual() * POR_PAGINA;
    return this.visible().slice(inicio, inicio + POR_PAGINA);
  });


  ngOnInit(): void {
    // Puede venir de otra pantalla con ?ciudad=&llegada=&salida=&huespedes=
    const q = this.route.snapshot.queryParamMap;
    this.ciudad.set(q.get('ciudad') ?? '');
    this.entrada.set(q.get('llegada') ?? '');
    this.salida.set(q.get('salida') ?? '');
    this.huespedes.set(q.get('huespedes') ?? '');
    // Desde la página de inicio se puede llegar con ?tipo= para ver solo ese tipo.
    const tipo = q.get('tipo');
    if (tipo) {
      this.filterType.set(tipo);
    }
    const error = this.busquedaService.validarBusqueda(this.camposBusqueda());
    if (error) {
      this.errorBusqueda.set(error);
      this.entrada.set('');
      this.salida.set('');
    }
    this.busquedaAplicada.set(this.hayFiltrosDeBusqueda());
    this.cargarAlojamientos();
  }

  /** Aplica la barra de búsqueda: actualiza la URL y vuelve a pedir los alojamientos. */
  buscar(): void {
    const error = this.busquedaService.validarBusqueda(this.camposBusqueda());
    this.errorBusqueda.set(error);
    if (error) {
      return;
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.busquedaService.paramsBusqueda(this.camposBusqueda()),
      replaceUrl: true,
    });
    this.busquedaAplicada.set(this.hayFiltrosDeBusqueda());
    this.cargarAlojamientos();
  }

  limpiarBusqueda(): void {
    this.ciudad.set('');
    this.entrada.set('');
    this.salida.set('');
    this.huespedes.set('');
    this.errorBusqueda.set(null);
    this.busquedaAplicada.set(false);
    void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    this.cargarAlojamientos();
  }

  reintentar(): void {
    this.cargarAlojamientos();
  }


  toggleServicio(servicio: string, seleccionado: boolean): void {
    this.serviciosSeleccionados.update((actuales) => {
      if (seleccionado) {
        return actuales.includes(servicio) ? actuales : [...actuales, servicio];
      }
      return actuales.filter((actual) => actual !== servicio);
    });
    this.pagina.set(0);
  }

  servicioSeleccionado(servicio: string): boolean {
    return this.serviciosSeleccionados().includes(servicio);
  }

  elegirTipo(tipo: string): void {
    this.filterType.set(tipo);
    this.pagina.set(0);
  }


  onSortChange(event: Event): void {
    const valor = (event.target as HTMLSelectElement).value as SortOption;
    if (this.sortOptions.includes(valor)) {
      this.sortBy.set(valor);
      this.pagina.set(0);
    }
  }


  actualizarPrecioMin(event: Event): void {
    const input = event.target as HTMLInputElement;
    const valor = Number(input.value);
    const nuevo =
      Number.isFinite(valor) && input.value !== ''
        ? Math.min(Math.max(valor, this.precioMinimoDisponible()), this.precioMax())
        : this.precioMin();
    this.precioMin.set(nuevo);
    input.value = String(nuevo);
    this.pagina.set(0);
  }

  actualizarPrecioMax(event: Event): void {
    const input = event.target as HTMLInputElement;
    const valor = Number(input.value);
    const nuevo =
      Number.isFinite(valor) && input.value !== ''
        ? Math.max(Math.min(valor, this.precioMaximoDisponible()), this.precioMin())
        : this.precioMax();
    this.precioMax.set(nuevo);
    input.value = String(nuevo);
    this.pagina.set(0);
  }


  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPaginas()) {
      this.pagina.set(pagina);
      window.scrollTo?.({ top: 0, behavior: 'smooth' });
    }
  }


  openStay(id: number | undefined): void {
    if (id != null) {
      void this.router.navigate(['/alojamientos', id]);
    }
  }


  limpiarFiltros(): void {
    this.filterType.set('Todos');
    this.serviciosSeleccionados.set([]);
    this.sortBy.set('Recomendados');
    this.precioMin.set(this.precioMinimoDisponible());
    this.precioMax.set(this.precioMaximoDisponible());
    this.pagina.set(0);
  }


  private camposBusqueda(): CamposBusqueda {
    return { ciudad: this.ciudad(), entrada: this.entrada(), salida: this.salida(), huespedes: this.huespedes() };
  }

  private hayFiltrosDeBusqueda(): boolean {
    return Object.keys(this.busquedaService.paramsBusqueda(this.camposBusqueda())).length > 0;
  }

  /** Sin filtros de búsqueda: catálogo completo. Con filtros: búsqueda del backend (incluye disponibilidad por fechas). */
  private pedirAlojamientos(): Observable<AlojamientoResultado[]> {
    if (!this.hayFiltrosDeBusqueda()) {
      return this.busquedaService.disponibles(100);
    }
    return this.busquedaService.buscar(this.busquedaService.paramsBusqueda(this.camposBusqueda()), 100);
  }

  private cargarAlojamientos(): void {
    this.loading.set(true);
    this.errorCarga.set(false);
    this.pagina.set(0);
    this.pedirAlojamientos().subscribe({
      next: (respuesta) => {
        this.stays.set(respuesta ?? []);
        this.precioMin.set(this.precioMinimoDisponible());
        this.precioMax.set(this.precioMaximoDisponible());
        this.loading.set(false);
      },
      error: () => {
        this.stays.set([]);
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }

  private tieneServiciosSeleccionados(alojamiento: AlojamientoResultado): boolean {
    const seleccionados = this.serviciosSeleccionados();
    if (seleccionados.length === 0) {
      return true;
    }
    const amenidades = (alojamiento.amenidades ?? []).map((a) => a.trim().toLocaleLowerCase('es'));
    return seleccionados.every((s) => amenidades.includes(s.toLocaleLowerCase('es')));
  }

  private aPorcentaje(valor: number): number {
    const min = this.precioMinimoDisponible();
    const rango = this.precioMaximoDisponible() - min;
    if (rango <= 0) {
      return 0;
    }
    return Math.min(100, Math.max(0, ((valor - min) / rango) * 100));
  }
}
