import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { AlojamientoResultado, CamposBusqueda, ConsultaExplorar, FiltrosExplorar } from '../models/busqueda';
import { BusquedaService } from '../services/busqueda.service';

type SortOption = 'Recomendados' | 'Precio menor' | 'Precio mayor' | 'Mejor calificados';

/** Alojamientos por página en los resultados. */
const POR_PAGINA = 9;

/** Espera tras mover el control de precio antes de pedir la página (evita una petición por cada paso). */
const ESPERA_PRECIO_MS = 300;

/** Orden que entiende el backend para cada opción; "Recomendados" usa el orden por defecto de cada endpoint. */
const ORDEN: Record<SortOption, string | undefined> = {
  Recomendados: undefined,
  'Precio menor': 'precio_menor',
  'Precio mayor': 'precio_mayor',
  'Mejor calificados': 'mejor_calificados',
};

/** Sin opciones de filtro (antes de cargarlas o si fallan). */
const SIN_FILTROS: FiltrosExplorar = { tipos: [], amenidades: [], precioMinimo: null, precioMaximo: null };

/**
 * Buscar alojamientos con filtros (HU-23): ciudad, fechas, huéspedes, tipo, precio y servicios,
 * con listado paginado y estados de carga, vacío y error. La paginación y los filtros se resuelven
 * en el servidor: solo se descargan los alojamientos de la página que se muestra.
 */
@Component({
  selector: 'app-buscar-alojamientos',
  standalone: true,
  templateUrl: './buscar-alojamientos.html',
  imports: [CurrencyPipe, DecimalPipe],
})
export class BuscarAlojamientos implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly busquedaService = inject(BusquedaService);

  /** Alojamientos de la página actual. */
  readonly stays = signal<AlojamientoResultado[]>([]);
  /** Total de alojamientos que cumplen los filtros (todas las páginas). */
  readonly total = signal(0);
  private readonly paginas = signal(1);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);

  /** Opciones de los filtros para el conjunto que se explora. */
  private readonly opciones = signal<FiltrosExplorar>(SIN_FILTROS);

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

  /** Identifica la última petición: las respuestas de peticiones anteriores se descartan. */
  private ultimaPeticion = 0;
  private esperaPrecio: ReturnType<typeof setTimeout> | undefined;

  readonly precioMinimoDisponible = computed(() => this.opciones().precioMinimo ?? 0);
  readonly precioMaximoDisponible = computed(() => this.opciones().precioMaximo ?? 0);
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

  readonly types = computed(() => ['Todos', ...this.opciones().tipos]);
  readonly services = computed(() => this.opciones().amenidades);

  /** Alojamientos que se muestran (la página actual, ya filtrada y ordenada por el servidor). */
  readonly visible = computed(() => this.stays());
  readonly totalPaginas = computed(() => Math.max(1, this.paginas()));
  readonly paginaActual = computed(() => Math.min(this.pagina(), this.totalPaginas() - 1));
  readonly paginados = computed(() => this.stays());

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

  ngOnDestroy(): void {
    clearTimeout(this.esperaPrecio);
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
    this.cargarPagina(0);
  }

  servicioSeleccionado(servicio: string): boolean {
    return this.serviciosSeleccionados().includes(servicio);
  }

  elegirTipo(tipo: string): void {
    this.filterType.set(tipo);
    this.cargarPagina(0);
  }

  onSortChange(event: Event): void {
    const valor = (event.target as HTMLSelectElement).value as SortOption;
    if (this.sortOptions.includes(valor)) {
      this.sortBy.set(valor);
      this.cargarPagina(0);
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
    this.programarCargaPorPrecio();
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
    this.programarCargaPorPrecio();
  }

  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPaginas() && pagina !== this.pagina()) {
      this.cargarPagina(pagina);
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
    this.cargarPagina(0);
  }

  private camposBusqueda(): CamposBusqueda {
    return { ciudad: this.ciudad(), entrada: this.entrada(), salida: this.salida(), huespedes: this.huespedes() };
  }

  private paramsBusqueda(): Record<string, string | number> {
    return this.busquedaService.paramsBusqueda(this.camposBusqueda());
  }

  private hayFiltrosDeBusqueda(): boolean {
    return Object.keys(this.paramsBusqueda()).length > 0;
  }

  /** Filtros del panel y orden, sin los que no filtran (precio en sus extremos, tipo "Todos"). */
  private consulta(): ConsultaExplorar {
    const consulta: ConsultaExplorar = { orden: ORDEN[this.sortBy()] };
    if (this.filterType() !== 'Todos') {
      consulta.tipo = this.filterType();
    }
    if (this.precioMin() > this.precioMinimoDisponible()) {
      consulta.precioMinimo = this.precioMin();
    }
    if (this.precioMax() < this.precioMaximoDisponible()) {
      consulta.precioMaximo = this.precioMax();
    }
    if (this.serviciosSeleccionados().length > 0) {
      consulta.amenidades = this.serviciosSeleccionados();
    }
    return consulta;
  }

  /** Pide las opciones de los filtros del catálogo o de la búsqueda y después la primera página. */
  private cargarAlojamientos(): void {
    this.loading.set(true);
    this.errorCarga.set(false);
    const peticion = ++this.ultimaPeticion;
    this.busquedaService.filtros(this.paramsBusqueda()).subscribe({
      next: (opciones) => {
        if (peticion !== this.ultimaPeticion) {
          return;
        }
        this.opciones.set({ ...SIN_FILTROS, ...opciones });
        this.precioMin.set(this.precioMinimoDisponible());
        this.precioMax.set(this.precioMaximoDisponible());
        this.serviciosSeleccionados.update((s) => s.filter((servicio) => this.services().includes(servicio)));
        this.cargarPagina(0);
      },
      error: () => this.mostrarError(peticion),
    });
  }

  /** Pide una página con los filtros actuales; las respuestas de peticiones anteriores se descartan. */
  private cargarPagina(pagina: number): void {
    clearTimeout(this.esperaPrecio);
    this.loading.set(true);
    this.errorCarga.set(false);
    this.pagina.set(pagina);
    const peticion = ++this.ultimaPeticion;
    this.busquedaService.pagina(this.paramsBusqueda(), this.consulta(), pagina, POR_PAGINA).subscribe({
      next: (respuesta) => {
        if (peticion !== this.ultimaPeticion) {
          return;
        }
        this.stays.set(respuesta?.contenido ?? respuesta?.content ?? []);
        this.total.set(respuesta?.totalElementos ?? this.stays().length);
        this.paginas.set(respuesta?.totalPaginas ?? 1);
        this.loading.set(false);
      },
      error: () => this.mostrarError(peticion),
    });
  }

  private programarCargaPorPrecio(): void {
    clearTimeout(this.esperaPrecio);
    this.esperaPrecio = setTimeout(() => this.cargarPagina(0), ESPERA_PRECIO_MS);
  }

  private mostrarError(peticion: number): void {
    if (peticion !== this.ultimaPeticion) {
      return;
    }
    this.stays.set([]);
    this.total.set(0);
    this.paginas.set(1);
    this.errorCarga.set(true);
    this.loading.set(false);
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
