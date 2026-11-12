import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { DetalleAlojamiento, ResenaDetalle } from '../models/detalle-alojamiento';
import { DetalleAlojamientoService } from '../services/detalle-alojamiento.service';
import { SessionService } from '../../core/services/session.service';
import { FavoritoService } from '../../favoritos/services/favorito.service';
import { MensajeriaService } from '../../mensajeria/services/mensajeria.service';

/** Máximo de fotos de la galería: la principal y 4 pequeñas. */
const MAX_FOTOS = 5;

/** Distancia (metros) que se desplaza el punto del mapa para no mostrar la dirección exacta. */
const DESPLAZAMIENTO_M = 300;
/** Margen (grados) alrededor del punto para el recuadro del mapa. */
const MARGEN_MAPA = 0.01;

/**
 * Detalle de un alojamiento (HU-18): galería, información, servicios, reglas, reseñas y,
 * como último bloque, el mapa con la ubicación aproximada (CA-164). El huésped puede guardarlo
 * en sus favoritos (HU-29, CA-173) y contactar al anfitrión (HU-28, CA-169).
 */
@Component({
  selector: 'app-detalle-alojamiento',
  standalone: true,
  templateUrl: './detalle-alojamiento.html',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, RouterLink],
})
export class DetalleAlojamientoPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly servicio = inject(DetalleAlojamientoService);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly session = inject(SessionService);
  protected readonly favoritoService = inject(FavoritoService);
  private readonly router = inject(Router);
  private readonly mensajeria = inject(MensajeriaService);

  // ==================== Alojamiento ====================
  readonly stay = signal<DetalleAlojamiento | null>(null);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);

  readonly amenities = computed(() => this.stay()?.amenidades ?? []);
  readonly servicios = computed(() => this.stay()?.serviciosAdicionales ?? []);

  /** Solo las fotos que tiene el alojamiento (sin fotos de relleno), la principal primero. */
  readonly fotos = computed(() =>
    (this.stay()?.imagenes ?? []).filter((url) => !!url?.trim()).slice(0, MAX_FOTOS),
  );
  /** Foto grande de la galería; null si el alojamiento no tiene fotos. */
  readonly fotoPrincipal = computed(() => this.fotos()[0] ?? null);
  /** Hasta 4 fotos pequeñas, solo si el alojamiento las tiene. */
  readonly fotosSecundarias = computed(() => this.fotos().slice(1));

  /** Reglas de la casa: horarios, política de cancelación y reglas escritas por el anfitrión. */
  readonly reglas = computed(() => {
    const s = this.stay();
    if (!s) {
      return [];
    }
    return [
      { title: 'Entrada', text: s.horaEntrada ? `A partir de las ${this.hora(s.horaEntrada)}` : '' },
      { title: 'Salida', text: s.horaSalida ? `Antes de las ${this.hora(s.horaSalida)}` : '' },
      { title: 'Política de cancelación', text: s.nombrePolitica ?? '' },
      { title: 'Reglas del anfitrión', text: s.reglas ?? '' },
    ].filter((r) => !!r.text);
  });

  // ==================== Reseñas ====================
  readonly reviews = signal<ResenaDetalle[]>([]);
  readonly promedioResenas = signal(0);
  readonly cantidadResenas = signal(0);
  readonly starList = [1, 2, 3, 4, 5];
  readonly visibleReviews = computed(() => this.reviews().slice(0, 4));
  readonly distribution = computed(() => {
    const lista = this.reviews();
    return [5, 4, 3, 2, 1].map((stars) => {
      const count = lista.filter((r) => r.calificacion === stars).length;
      return { stars, count, width: lista.length > 0 ? (count / lista.length) * 100 : 0 };
    });
  });

  // ==================== Información visual ====================
  readonly highlights = computed(() => [
    { icon: 'home', title: 'Un espacio solo para ti', text: 'Tendrás todo el alojamiento durante tu estancia.' },
    {
      icon: 'pin',
      title: 'Ubicación privilegiada',
      text: `Cerca de naturaleza, restaurantes y puntos de interés de ${this.stay()?.ciudad ?? 'la ciudad'}.`,
    },
    {
      icon: 'star',
      title: 'Experiencia destacada',
      text: `${this.stay()?.cantidadResenas ?? 0} huéspedes han calificado este alojamiento.`,
    },
  ]);

  // ==================== Mapa (CA-164) ====================
  /** Punto aproximado: las coordenadas reales desplazadas unos metros por privacidad. */
  readonly puntoAproximado = computed<[number, number] | null>(() => {
    const s = this.stay();
    const lat = s?.latitud;
    const lng = s?.longitud;
    if (typeof lat !== 'number' || typeof lng !== 'number' || !Number.isFinite(lat) || !Number.isFinite(lng)) {
      return null;
    }
    const angulo = ((s?.idAlojamiento ?? 0) * 137) % 360 * (Math.PI / 180);
    const dLat = (DESPLAZAMIENTO_M * Math.cos(angulo)) / 111320;
    const dLng = (DESPLAZAMIENTO_M * Math.sin(angulo)) / (111320 * Math.cos((lat * Math.PI) / 180));
    return [lat + dLat, lng + dLng];
  });

  /** Mapa embebido de OpenStreetMap; null si el alojamiento no tiene coordenadas. */
  readonly mapaUrl = computed<SafeResourceUrl | null>(() => {
    const p = this.puntoAproximado();
    if (!p) {
      return null;
    }
    const [lat, lng] = p;
    const bbox = [lng - MARGEN_MAPA, lat - MARGEN_MAPA, lng + MARGEN_MAPA, lat + MARGEN_MAPA]
      .map((n) => n.toFixed(5))
      .join(',');
    const url = `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${lat.toFixed(5)},${lng.toFixed(5)}`;
    // La URL se arma solo con números y el dominio fijo de OpenStreetMap.
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  });

  /** Enlace para abrir el mapa completo en OpenStreetMap. */
  readonly mapaEnlace = computed(() => {
    const p = this.puntoAproximado();
    if (p) {
      return `https://www.openstreetmap.org/?mlat=${p[0].toFixed(5)}&mlon=${p[1].toFixed(5)}#map=15/${p[0].toFixed(5)}/${p[1].toFixed(5)}`;
    }
    const ciudad = this.stay()?.ciudad ?? '';
    return `https://www.openstreetmap.org/search?query=${encodeURIComponent(ciudad)}`;
  });

  readonly horizontalLines = [18, 38, 58, 78];
  readonly verticalLines = [15, 35, 55, 75];

  // ==================== Favoritos (HU-29) ====================
  /** true si el alojamiento está en los favoritos del huésped, según el backend. */
  readonly favorite = computed(() => this.favoritoService.esFavorito(this.stay()?.idAlojamiento));
  readonly favoritoEnCurso = computed(() => this.favoritoService.enCurso(this.stay()?.idAlojamiento));

  toggleFavorite(): void {
    this.favoritoService.toggle(this.stay()?.idAlojamiento);
  }

  // ==================== Contactar al anfitrión (HU-28) ====================
  readonly contactando = signal(false);
  readonly errorContacto = signal<string | null>(null);

  /** Inicia (o retoma) la conversación con el anfitrión y abre la mensajería en ella (CA-169). */
  contactHost(): void {
    const id = this.stay()?.idAlojamiento;
    if (id == null || this.contactando()) {
      return;
    }
    this.contactando.set(true);
    this.errorContacto.set(null);
    this.mensajeria.iniciar(id).subscribe({
      next: (conversacion) => {
        this.contactando.set(false);
        void this.router.navigate(['/mensajes'], { queryParams: { conversacion: conversacion.id } });
      },
      error: (error: unknown) => {
        this.contactando.set(false);
        this.errorContacto.set(this.mensajeria.mensajeDeError(error, 'No se pudo iniciar la conversación.'));
      },
    });
  }

  // ==================== Inicialización ====================
  ngOnInit(): void {
    this.favoritoService.mensaje.set(null);
    this.favoritoService.cargarSiCorresponde();
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) {
      this.errorCarga.set(true);
      this.loading.set(false);
      return;
    }
    this.servicio.obtener(id).subscribe({
      next: (respuesta) => {
        this.stay.set(respuesta);
        this.promedioResenas.set(respuesta.promedioCalificacion ?? 0);
        this.cantidadResenas.set(respuesta.cantidadResenas ?? 0);
        this.loading.set(false);
        this.cargarResenas(id);
      },
      error: () => {
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }

  initials(name: string): string {
    return name
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .map((word) => word[0])
      .join('')
      .toUpperCase();
  }

  /** Si las reseñas fallan la página sigue funcionando con el promedio del alojamiento. */
  private cargarResenas(id: number): void {
    this.servicio.resenas(id).subscribe({
      next: (r) => {
        this.reviews.set(r.resenas?.contenido ?? []);
        if (r.promedio != null) {
          this.promedioResenas.set(r.promedio);
        }
        if (r.cantidad != null) {
          this.cantidadResenas.set(r.cantidad);
        }
      },
      error: () => this.reviews.set([]),
    });
  }

  /** "15:00:00" → "15:00". */
  private hora(valor: string): string {
    return valor.slice(0, 5);
  }
}
