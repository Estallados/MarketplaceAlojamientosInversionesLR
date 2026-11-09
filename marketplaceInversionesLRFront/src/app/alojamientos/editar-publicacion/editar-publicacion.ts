import {
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal
} from '@angular/core';

import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { finalize, map, switchMap } from 'rxjs';

import {
  AlojamientoRespuesta,
  DatosAlojamiento,
  EstadoAlojamiento,
  PoliticaCancelacion
} from '../models/alojamiento';

import { PublicacionHostService } from '../services/publicacion-host.service';
import { PermisoPublicacionService } from '../services/permiso-publicacion.service';
import { SessionService } from '../../services/session.service';

import {
  TEXTO_CORTO,
  TEXTO_LARGO,
  errorCapacidad,
  errorLongitud,
  errorNoNegativo,
  errorPrecioNoche,
  errorTitulo
} from '../validaciones/validaciones-alojamiento';

// ======================================
// INTERFAZ DEL FORMULARIO
// ======================================

interface FormularioEdicion {
  titulo: string;
  ciudad: string;
  descripcion: string;
  ubicacion: string;
  tipo: string;
  capacidad: number | null;
  habitaciones: number | null;
  camas: number | null;
  banos: number | null;
  precioNoche: number | null;
  horaEntrada: string;
  horaSalida: string;
  reglas: string;
  idPolitica: number | null;
  amenidades: string[];
}

/** Campos que se validan uno por uno. */
export type CampoEdicion =
  | 'titulo'
  | 'ciudad'
  | 'descripcion'
  | 'ubicacion'
  | 'tipo'
  | 'capacidad'
  | 'habitaciones'
  | 'camas'
  | 'banos'
  | 'precioNoche'
  | 'reglas';

type CampoNumerico =
  | 'capacidad'
  | 'habitaciones'
  | 'camas'
  | 'banos'
  | 'precioNoche';

/**
 * Resultado de cargar la publicación:
 * - cargando: esperando al servidor.
 * - permitido: propietario o administrador (CA-113).
 * - denegado: no es propietario ni administrador (CA-101, CA-114).
 * - sin-sesion: el servidor respondió 401.
 * - no-encontrado: el alojamiento no existe o no es visible.
 * - error: cualquier otro fallo.
 */
export type EstadoAcceso =
  | 'cargando'
  | 'permitido'
  | 'denegado'
  | 'sin-sesion'
  | 'no-encontrado'
  | 'error';

const CAMPOS: CampoEdicion[] = [
  'titulo',
  'ciudad',
  'descripcion',
  'ubicacion',
  'tipo',
  'capacidad',
  'habitaciones',
  'camas',
  'banos',
  'precioNoche',
  'reglas'
];

/** Mensaje que se muestra cuando no tiene permiso (CA-101, CA-114). */
export const MENSAJE_SIN_PERMISO =
  'Solo el anfitrión propietario o un administrador ' +
  'puede modificar esta publicación.';

// ======================================
// COMPONENTE
// HU-16: actualizar publicación (CA-100) con control
// de propietario o administrador (CA-101, CA-113, CA-114).
// ======================================

@Component({
  selector: 'app-editar-publicacion',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './editar-publicacion.html'
})
export class EditarPublicacion implements OnInit {

  // ======================================
  // SERVICIOS
  // ======================================

  private readonly publicaciones =
    inject(PublicacionHostService);

  private readonly permisos =
    inject(PermisoPublicacionService);

  private readonly sesion =
    inject(SessionService);

  private readonly route =
    inject(ActivatedRoute);

  private readonly destroyRef =
    inject(DestroyRef);

  // ======================================
  // DATOS DE LA PUBLICACIÓN
  // ======================================

  readonly idAlojamiento =
    Number(this.route.snapshot.paramMap.get('id'));

  readonly alojamiento =
    signal<AlojamientoRespuesta | null>(null);

  readonly acceso =
    signal<EstadoAcceso>('cargando');

  readonly politicas =
    signal<PoliticaCancelacion[]>([]);

  readonly guardando =
    signal(false);

  readonly error =
    signal('');

  readonly mensaje =
    signal('');

  /** El administrador edita publicaciones de otros anfitriones (CA-113). */
  readonly esAdministrador =
    this.sesion.tieneRol('Administrador');

  readonly servicios = [
    'Wifi',
    'Piscina',
    'Parqueadero',
    'Cocina',
    'Aire acondicionado',
    'Lavadora',
    'Televisor',
    'Jacuzzi',
    'Admite mascotas'
  ];

  readonly tipos = [
    'Casa',
    'Apartamento',
    'Cabaña',
    'Finca',
    'Habitación',
    'Hotel'
  ];

  draft: FormularioEdicion = this.formularioDesde(null);

  /** Copia de lo guardado en el servidor para detectar cambios. */
  private original = '';

  private readonly tocados = new Set<CampoEdicion>();

  private readonly noNumericos = new Set<CampoNumerico>();

  intentoGuardar = false;

  // ======================================
  // 1. CARGAR LA PUBLICACIÓN Y EL PERMISO
  // GET /alojamientos/{id}
  // ======================================

  ngOnInit(): void {

    if (
      !Number.isInteger(this.idAlojamiento) ||
      this.idAlojamiento <= 0
    ) {

      this.acceso.set('no-encontrado');

      this.error.set('El identificador del alojamiento no es válido.');

      return;
    }

    this.publicaciones
      .obtenerAlojamiento(this.idAlojamiento)
      .pipe(

        // CA-113 / CA-101: propietario o administrador
        switchMap(alojamiento =>
          this.permisos
            .puedeModificar(alojamiento)
            .pipe(
              map(permitido => ({ alojamiento, permitido }))
            )
        ),

        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({

        next: ({ alojamiento, permitido }) => {

          if (!permitido) {

            this.acceso.set('denegado');

            this.error.set(MENSAJE_SIN_PERMISO);

            return;
          }

          this.cargarFormulario(alojamiento);

          this.acceso.set('permitido');

          this.cargarPoliticas();
        },

        error: err => {
          this.manejarError(err, true);
        }
      });
  }

  /** Políticas de cancelación disponibles (GET /alojamientos/politicas). */
  private cargarPoliticas(): void {

    this.publicaciones
      .obtenerPoliticas()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({

        next: politicas => {
          this.politicas.set(politicas);
        },

        error: () => {
          this.error.set(
            'No se pudieron cargar las políticas de cancelación. ' +
            'Puedes seguir editando la publicación.'
          );
        }
      });
  }

  private formularioDesde(
    a: AlojamientoRespuesta | null
  ): FormularioEdicion {

    return {
      titulo: a?.titulo ?? '',
      ciudad: a?.ciudad ?? '',
      descripcion: a?.descripcion ?? '',
      ubicacion: a?.ubicacion ?? '',
      tipo: a?.tipo ?? '',
      capacidad: a?.capacidad ?? null,
      habitaciones: a?.habitaciones ?? null,
      camas: a?.camas ?? null,
      banos: a?.banos ?? null,
      precioNoche: a?.precioNoche ?? null,
      // El backend envía LocalTime (HH:mm:ss); el input usa HH:mm.
      horaEntrada: a?.horaEntrada?.slice(0, 5) ?? '',
      horaSalida: a?.horaSalida?.slice(0, 5) ?? '',
      reglas: a?.reglas ?? '',
      idPolitica: a?.idPolitica ?? null,
      amenidades: [...(a?.amenidades ?? [])]
    };
  }

  /** Inicializa el formulario con los datos actuales (CA-100). */
  private cargarFormulario(
    alojamiento: AlojamientoRespuesta
  ): void {

    this.alojamiento.set(alojamiento);

    this.draft = this.formularioDesde(alojamiento);

    this.original = JSON.stringify(this.draft);

    this.tocados.clear();

    this.noNumericos.clear();

    this.intentoGuardar = false;
  }

  get puedeEditar(): boolean {
    return this.acceso() === 'permitido';
  }

  get hayCambios(): boolean {
    return JSON.stringify(this.draft) !== this.original;
  }

  // ======================================
  // 2. VALIDACIÓN POR CAMPO
  // ======================================

  marcarTocado(campo: CampoEdicion): void {
    this.tocados.add(campo);
  }

  revisarNumero(
    campo: CampoNumerico,
    evento: Event
  ): void {

    const input = evento.target as HTMLInputElement;

    if (input.validity?.badInput) {
      this.noNumericos.add(campo);
    } else {
      this.noNumericos.delete(campo);
    }
  }

  mostrarError(campo: CampoEdicion): boolean {

    return (
      (this.intentoGuardar || this.tocados.has(campo)) &&
      this.errorCampo(campo) !== ''
    );
  }

  errorCampo(campo: CampoEdicion): string {

    const d = this.draft;

    switch (campo) {

      case 'titulo':
        return errorTitulo(d.titulo);

      case 'ciudad':
        return errorLongitud(d.ciudad, TEXTO_CORTO);

      case 'ubicacion':
        return errorLongitud(d.ubicacion, TEXTO_CORTO);

      case 'tipo':
        return errorLongitud(d.tipo, TEXTO_CORTO);

      case 'descripcion':
        return errorLongitud(d.descripcion, TEXTO_LARGO);

      case 'reglas':
        return errorLongitud(d.reglas, TEXTO_LARGO);

      case 'capacidad':
        return this.errorVaciado('capacidad') ||
          errorCapacidad(d.capacidad, this.noNumericos.has('capacidad'));

      case 'precioNoche':
        return this.errorVaciado('precioNoche') ||
          errorPrecioNoche(d.precioNoche, this.noNumericos.has('precioNoche'));

      case 'habitaciones':
        return this.errorVaciado('habitaciones') ||
          errorNoNegativo('habitaciones', d.habitaciones, this.noNumericos.has('habitaciones'));

      case 'camas':
        return this.errorVaciado('camas') ||
          errorNoNegativo('camas', d.camas, this.noNumericos.has('camas'));

      case 'banos':
        return this.errorVaciado('banos') ||
          errorNoNegativo('baños', d.banos, this.noNumericos.has('banos'));
    }
  }

  /**
   * En la edición un campo numérico vacío no se envía y el backend conserva
   * el valor anterior; para no confundir, no se permite vaciar un dato que
   * ya estaba registrado.
   */
  private errorVaciado(campo: CampoNumerico): string {

    const anterior = this.alojamiento()?.[campo];

    if (
      !this.noNumericos.has(campo) &&
      this.draft[campo] === null &&
      anterior !== undefined &&
      anterior !== null
    ) {
      return 'Este dato ya estaba registrado; indica un valor.';
    }

    return '';
  }

  private primerError(): string | null {

    for (const campo of CAMPOS) {

      const problema = this.errorCampo(campo);

      if (problema) {
        return problema;
      }
    }

    return null;
  }

  // ======================================
  // 3. SERVICIOS INCLUIDOS (AMENIDADES)
  // ======================================

  tieneAmenidad(servicio: string): boolean {

    return this.draft.amenidades.some(
      a => a.toLowerCase() === servicio.toLowerCase()
    );
  }

  toggleAmenidad(servicio: string): void {

    this.draft.amenidades = this.tieneAmenidad(servicio)
      ? this.draft.amenidades.filter(
        a => a.toLowerCase() !== servicio.toLowerCase()
      )
      : [...this.draft.amenidades, servicio];
  }

  // ======================================
  // 4. GUARDAR CAMBIOS (CA-100)
  // PUT /alojamientos/{id}
  // ======================================

  guardar(): void {

    // Sin permiso no se envía nada (CA-101, CA-114). Evita envíos duplicados.

    if (!this.puedeEditar || this.guardando()) {
      return;
    }

    this.intentoGuardar = true;

    this.error.set('');
    this.mensaje.set('');

    const problema = this.primerError();

    if (problema) {

      this.error.set(
        'Revisa los datos marcados antes de guardar.'
      );

      return;
    }

    this.guardando.set(true);

    this.publicaciones
      .actualizarAlojamiento(
        this.idAlojamiento,
        this.construirDatos()
      )
      .pipe(
        finalize(() => {
          this.guardando.set(false);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({

        next: actualizado => {

          // Se muestra lo que devolvió el servidor.
          // Los servicios adicionales se gestionan aparte y no
          // cambian con el PUT, así que se conservan.

          const anterior = this.alojamiento();

          this.cargarFormulario({
            ...actualizado,
            serviciosAdicionales:
              actualizado.serviciosAdicionales ??
              anterior?.serviciosAdicionales
          });

          this.mensaje.set(
            'Los cambios de la publicación se guardaron correctamente.'
          );
        },

        error: err => {
          this.manejarError(err, false);
        }
      });
  }

  /** Descarta lo editado y vuelve a los datos guardados. */
  descartar(): void {

    const actual = this.alojamiento();

    if (!actual || this.guardando()) {
      return;
    }

    this.cargarFormulario(actual);

    this.error.set('');
    this.mensaje.set('');
  }

  private construirDatos(): DatosAlojamiento {

    const d = this.draft;

    return {

      titulo: d.titulo.trim(),

      descripcion: d.descripcion.trim(),

      ubicacion: d.ubicacion.trim(),

      ciudad: d.ciudad.trim(),

      tipo: d.tipo.trim(),

      ...(d.capacidad !== null
        ? { capacidad: d.capacidad }
        : {}),

      ...(d.habitaciones !== null
        ? { habitaciones: d.habitaciones }
        : {}),

      ...(d.camas !== null
        ? { camas: d.camas }
        : {}),

      ...(d.banos !== null
        ? { banos: d.banos }
        : {}),

      ...(d.precioNoche !== null
        ? { precioNoche: d.precioNoche }
        : {}),

      ...(d.idPolitica !== null
        ? { idPolitica: d.idPolitica }
        : {}),

      ...(d.horaEntrada
        ? { horaEntrada: d.horaEntrada }
        : {}),

      ...(d.horaSalida
        ? { horaSalida: d.horaSalida }
        : {}),

      reglas: d.reglas.trim(),

      amenidades: [...d.amenidades]
    };
  }

  // ======================================
  // 5. PERMISO REVOCADO DESDE LOS SERVICIOS ADICIONALES
  // ======================================

  /** El backend respondió 401 o 403 en una operación de servicios. */
  accesoRevocado(estado: 'denegado' | 'sin-sesion'): void {

    this.acceso.set(estado);

    this.mensaje.set('');

    this.error.set(
      estado === 'denegado'
        ? MENSAJE_SIN_PERMISO
        : 'Tu sesión expiró. Inicia sesión nuevamente.'
    );
  }

  // ======================================
  // 6. ESTADO Y MENSAJES
  // ======================================

  nombreEstado(estado: EstadoAlojamiento): string {

    const nombres: Record<EstadoAlojamiento, string> = {
      BORRADOR: 'Borrador',
      PENDIENTE_REVISION: 'Pendiente de revisión',
      ACTIVA: 'Activa',
      PAUSADA: 'Pausada',
      BLOQUEADA: 'Bloqueada'
    };

    return nombres[estado];
  }

  /**
   * @param alCargar true si el error ocurrió al cargar la publicación
   */
  private manejarError(
    err: unknown,
    alCargar: boolean
  ): void {

    if (err instanceof HttpErrorResponse) {

      // CA-101 / CA-114: el servidor rechaza la operación

      if (err.status === 403) {
        this.accesoRevocado('denegado');
        return;
      }

      if (err.status === 401) {
        this.accesoRevocado('sin-sesion');
        return;
      }

      if (err.status === 404) {

        this.acceso.set('no-encontrado');

        this.error.set(
          'La publicación no existe o no está disponible.'
        );

        return;
      }
    }

    if (alCargar) {
      this.acceso.set('error');
    }

    this.error.set(this.mensajeError(err));
  }

  private mensajeError(err: unknown): string {

    if (err instanceof HttpErrorResponse) {

      if (err.status === 0) {

        return (
          'No se pudo conectar con Spring Boot ' +
          'en localhost:8081.'
        );
      }

      if (
        err.error &&
        typeof err.error.message === 'string'
      ) {
        return err.error.message;
      }

      return (
        'Ocurrió un error en el servidor ' +
        `(HTTP ${err.status}).`
      );
    }

    return (
      'No se pudo completar la operación. ' +
      'Revisa la conexión e inténtalo nuevamente.'
    );
  }
}
