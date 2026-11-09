import {
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal
} from '@angular/core';

import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {
  Observable,
  finalize,
  map,
  of,
  switchMap,
  tap
} from 'rxjs';

import {
  AlojamientoRespuesta,
  DatosAlojamiento,
  EstadoAlojamiento,
  PoliticaCancelacion
} from '../models/alojamiento';

import { PublicacionHostService } from '../services/publicacion-host.service';

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

interface FormularioAlojamiento {
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

// ======================================
// PASOS DEL FORMULARIO
// Las secciones del formulario de referencia
// ======================================

export type NumeroPaso = 1 | 2 | 3 | 4;

/** Campos que se validan uno por uno. */
export type CampoValidado =
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

/** Dato que falta para enviar a revisión (CA-097). */
export interface DatoFaltante {
  nombre: string;
  paso: NumeroPaso;
}

/** Campos numéricos del formulario. */
type CampoNumerico =
  | 'capacidad'
  | 'habitaciones'
  | 'camas'
  | 'banos'
  | 'precioNoche';

const MAXIMO_IMAGENES = 15;

// ======================================
// COMPONENTE
// ======================================

@Component({
  selector: 'app-crear-publicacion',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './crear-publicacion.html'
})
export class CrearPublicacion implements OnInit {

  // ======================================
  // SERVICIOS
  // ======================================

  private readonly publicaciones =
    inject(PublicacionHostService);

  private readonly destroyRef = inject(DestroyRef);

  // ======================================
  // PASOS
  // ======================================

  readonly pasos: { numero: NumeroPaso; titulo: string }[] = [
    { numero: 1, titulo: 'Información del alojamiento' },
    { numero: 2, titulo: 'Horarios y reglas' },
    { numero: 3, titulo: 'Servicios incluidos' },
    { numero: 4, titulo: 'Fotografías del alojamiento' }
  ];

  readonly paso = signal<NumeroPaso>(1);

  /** Campos de cada paso que se validan antes de avanzar. */
  private readonly camposPorPaso: Record<NumeroPaso, CampoValidado[]> = {
    1: [
      'titulo',
      'ciudad',
      'descripcion',
      'ubicacion',
      'tipo',
      'capacidad',
      'habitaciones',
      'camas',
      'banos',
      'precioNoche'
    ],
    2: ['reglas'],
    3: [],
    4: []
  };

  // ======================================
  // SERVICIOS INCLUIDOS
  // ======================================

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

  // ======================================
  // FORMULARIO DEL ALOJAMIENTO
  // ======================================

  draft: FormularioAlojamiento = {
    titulo: '',
    ciudad: '',
    descripcion: '',
    ubicacion: '',
    tipo: '',
    capacidad: null,
    habitaciones: null,
    camas: null,
    banos: null,
    precioNoche: null,
    horaEntrada: '',
    horaSalida: '',
    reglas: '',
    idPolitica: null,
    amenidades: []
  };

  // ======================================
  // ESTADOS
  // ======================================

  readonly politicas =
    signal<PoliticaCancelacion[]>([]);

  readonly cargandoPoliticas =
    signal(false);

  readonly borradorId =
    signal<number | null>(null);

  readonly estado =
    signal<EstadoAlojamiento | null>(null);

  readonly imagenesGuardadas =
    signal(0);

  readonly guardando =
    signal(false);

  readonly error =
    signal('');

  readonly mensaje =
    signal('');

  /** Datos que faltan para enviar a revisión (CA-097). */
  readonly faltantes =
    signal<DatoFaltante[]>([]);

  /** Campos que el usuario ya visitó. */
  private readonly tocados = new Set<CampoValidado>();

  /** Campos numéricos con texto que no es un número. */
  private readonly noNumericos = new Set<CampoNumerico>();

  /** Se activa al intentar guardar para mostrar todos los errores. */
  intentoGuardar = false;

  // ======================================
  // IMÁGENES
  // ======================================

  imagenesSeleccionadas: File[] = [];

  // ======================================
  // 1. INICIALIZAR COMPONENTE
  // ======================================

  ngOnInit(): void {

    this.cargandoPoliticas.set(true);

    this.publicaciones.obtenerPoliticas()
      .pipe(
        finalize(() => {
          this.cargandoPoliticas.set(false);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({

        next: politicas => {
          this.politicas.set(politicas);
        },

        error: err => {
          this.error.set(
            this.mensajeError(err)
          );
        }
      });
  }

  // ======================================
  // 2. NAVEGACIÓN ENTRE PASOS
  // ======================================

  get progreso(): number {
    return (this.paso() / this.pasos.length) * 100;
  }

  siguiente(): void {

    const actual = this.paso();

    // Validar los campos del paso antes de avanzar.

    for (const campo of this.camposPorPaso[actual]) {
      this.tocados.add(campo);
    }

    const problema =
      this.primerErrorDelPaso(actual);

    if (problema) {

      this.error.set(problema);

      return;
    }

    this.error.set('');

    if (actual < this.pasos.length) {
      this.paso.set((actual + 1) as NumeroPaso);
    }
  }

  anterior(): void {

    const actual = this.paso();

    if (actual > 1) {

      this.error.set('');

      this.paso.set((actual - 1) as NumeroPaso);
    }
  }

  irAPaso(numero: NumeroPaso): void {

    this.paso.set(numero);
  }

  // ======================================
  // 3. VALIDACIÓN POR CAMPO
  // ======================================

  marcarTocado(campo: CampoValidado): void {
    this.tocados.add(campo);
  }

  /** Registra si el navegador no pudo leer el número escrito. */
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

  mostrarError(campo: CampoValidado): boolean {

    return (
      (this.intentoGuardar || this.tocados.has(campo)) &&
      this.errorCampo(campo) !== ''
    );
  }

  errorCampo(campo: CampoValidado): string {

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

      // CA-098: capacidad y precio mayores que cero

      case 'capacidad':
        return errorCapacidad(
          d.capacidad,
          this.noNumericos.has('capacidad')
        );

      case 'precioNoche':
        return errorPrecioNoche(
          d.precioNoche,
          this.noNumericos.has('precioNoche')
        );

      // Habitaciones, camas y baños

      case 'habitaciones':
        return errorNoNegativo(
          'habitaciones',
          d.habitaciones,
          this.noNumericos.has('habitaciones')
        );

      case 'camas':
        return errorNoNegativo(
          'camas',
          d.camas,
          this.noNumericos.has('camas')
        );

      case 'banos':
        return errorNoNegativo(
          'baños',
          d.banos,
          this.noNumericos.has('banos')
        );
    }
  }

  private primerErrorDelPaso(
    numero: NumeroPaso
  ): string | null {

    for (const campo of this.camposPorPaso[numero]) {

      const problema = this.errorCampo(campo);

      if (problema) {
        return problema;
      }
    }

    return null;
  }

  // ======================================
  // 4. SELECCIONAR SERVICIOS
  // ======================================

  toggleService(servicio: string): void {

    const seleccionados =
      this.draft.amenidades;

    if (seleccionados.includes(servicio)) {

      this.draft.amenidades =
        seleccionados.filter(
          s => s !== servicio
        );

    } else {

      this.draft.amenidades = [
        ...seleccionados,
        servicio
      ];
    }
  }

  // ======================================
  // 5. SELECCIONAR FOTOGRAFÍAS
  // ======================================

  seleccionarImagenes(evento: Event): void {

    const input =
      evento.target as HTMLInputElement;

    const nuevas =
      Array.from(input.files ?? []);

    input.value = '';

    this.agregarImagenes(nuevas);
  }

  agregarImagenes(nuevas: File[]): void {

    if (nuevas.length === 0) {
      return;
    }

    const tiposPermitidos = [
      'image/jpeg',
      'image/png',
      'image/webp'
    ];

    const archivoInvalido = nuevas.find(
      archivo =>
        !tiposPermitidos.includes(archivo.type) ||
        archivo.size === 0 ||
        archivo.size > 5 * 1024 * 1024
    );

    if (archivoInvalido) {

      this.error.set(
        `La fotografía ${archivoInvalido.name} ` +
        'no es válida. Usa JPG, PNG o WEBP ' +
        'de máximo 5 MB.'
      );

      return;
    }

    const cantidadTotal =
      this.imagenesGuardadas() +
      this.imagenesSeleccionadas.length +
      nuevas.length;

    if (cantidadTotal > MAXIMO_IMAGENES) {

      this.error.set(
        'Puedes cargar máximo 15 fotografías por alojamiento.'
      );

      return;
    }

    this.imagenesSeleccionadas = [
      ...this.imagenesSeleccionadas,
      ...nuevas
    ];

    this.error.set('');
  }

  // ======================================
  // 6. QUITAR FOTOGRAFÍA
  // ======================================

  quitarImagen(indice: number): void {

    this.imagenesSeleccionadas =
      this.imagenesSeleccionadas.filter(
        (_, i) => i !== indice
      );
  }

  // ======================================
  // 7. GUARDAR BORRADOR
  // ======================================

  saveDraft(): void {
    this.guardar(false);
  }

  // ======================================
  // 8. ENVIAR A REVISIÓN
  // ======================================

  submit(): void {
    this.guardar(true);
  }

  // ======================================
  // 9. PROCESO PRINCIPAL DE GUARDADO
  // ======================================

  private guardar(
    enviarARevision: boolean
  ): void {

    // Evita publicaciones duplicadas
    // por clics repetidos.

    if (this.guardando()) {
      return;
    }

    this.intentoGuardar = true;

    this.error.set('');
    this.mensaje.set('');
    this.faltantes.set([]);

    // Validar información.
    const problema =
      this.validar(enviarARevision);

    if (problema) {

      this.error.set(problema);

      return;
    }

    this.guardando.set(true);

    const datos =
      this.construirDatos();

    // Indica si el borrador se creó en esta misma
    // operación y en qué etapa ocurrió un error.

    const eraNuevo =
      this.borradorId() === null;

    let etapa: 'datos' | 'revision' = 'datos';

    // PASO 1:
    // Crear o actualizar alojamiento.

    this.guardarDatos(datos)
      .pipe(

        // PASO 2:
        // Enviar a revisión, si corresponde.

        switchMap(alojamiento => {

          if (!enviarARevision) {
            return of(alojamiento);
          }

          etapa = 'revision';

          return this.publicaciones
            .enviarRevision(
              alojamiento.idAlojamiento
            );
        }),

        finalize(() => {
          this.guardando.set(false);
        }),

        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({

        next: alojamiento => {

          this.estado.set(alojamiento.estado);

          this.intentoGuardar = false;

          if (enviarARevision) {

            this.mensaje.set(
              'El alojamiento fue enviado a revisión correctamente.'
            );

          } else {

            this.mensaje.set(
              `Borrador #${alojamiento.idAlojamiento} ` +
              'guardado correctamente.'
            );
          }
        },

        error: err => {

          const id =
            this.borradorId();

          // El borrador se creó, pero la operación siguiente
          // no existe todavía en el servidor (404).

          if (
            id !== null &&
            err instanceof HttpErrorResponse &&
            err.status === 404
          ) {

            if (eraNuevo) {

              this.intentoGuardar = false;

              this.mensaje.set(
                `Borrador #${id} guardado correctamente.`
              );
            }

            this.error.set(
              etapa === 'revision'
                ? `El borrador #${id} está guardado, pero no se pudo ` +
                  'enviar a revisión: el servidor todavía no tiene ' +
                  'disponible esta operación.'
                : `El borrador #${id} ya está guardado, pero el servidor ` +
                  'todavía no permite actualizarlo ni agregarle fotografías.'
            );

            return;
          }

          let texto =
            this.mensajeError(err);

          if (id !== null) {

            texto +=
              ` El borrador #${id} se conserva. ` +
              'Revisa los datos ' +
              'antes de volver a intentarlo.';
          }

          this.error.set(texto);
        }
      });
  }

  // ======================================
  // 10. CREAR O ACTUALIZAR ALOJAMIENTO
  // ======================================

  private guardarDatos(
    datos: DatosAlojamiento
  ): Observable<AlojamientoRespuesta> {

    const id =
      this.borradorId();

    const archivos = [
      ...this.imagenesSeleccionadas
    ];

    // CREAR UN NUEVO BORRADOR.

    if (id === null) {

      return this.publicaciones
        .crearBorrador(
          datos,
          archivos
        )
        .pipe(

          tap(alojamiento => {

            this.borradorId.set(
              alojamiento.idAlojamiento
            );

            this.estado.set(alojamiento.estado);

            const totalImagenes =
              alojamiento.detalleImagenes?.length ??
              alojamiento.imagenes?.length ??
              archivos.length;

            this.imagenesGuardadas.set(
              totalImagenes
            );

            this.imagenesSeleccionadas = [];
          })
        );
    }

    // ACTUALIZAR BORRADOR EXISTENTE.

    return this.publicaciones
      .actualizarBorrador(id, datos)
      .pipe(

        switchMap(alojamiento => {

          if (archivos.length === 0) {
            return of(alojamiento);
          }

          return this.publicaciones
            .agregarImagenes(
              id,
              archivos
            )
            .pipe(

              tap(imagenes => {

                this.imagenesGuardadas.set(
                  imagenes.length
                );

                this.imagenesSeleccionadas = [];
              }),

              map(() => alojamiento)
            );
        })
      );
  }

  // ======================================
  // 11. VALIDAR FORMULARIO
  // ======================================

  private validar(
    enviarARevision: boolean
  ): string | null {

    // Errores de formato de cada paso.
    // Se lleva al usuario al primer paso con error.

    for (const { numero } of this.pasos) {

      const problema =
        this.primerErrorDelPaso(numero);

      if (problema) {

        this.paso.set(numero);

        return problema;
      }
    }

    // Para crear el alojamiento, el backend
    // exige al menos una fotografía.

    if (
      this.borradorId() === null &&
      this.imagenesSeleccionadas.length === 0
    ) {

      this.paso.set(4);

      return (
        'Debes seleccionar al menos una fotografía ' +
        'para crear el alojamiento.'
      );
    }

    // Guardar borrador permite
    // datos incompletos.

    if (!enviarARevision) {
      return null;
    }

    // ====================================
    // AL ENVIAR A REVISIÓN (CA-097)
    // ====================================

    const faltantes =
      this.datosFaltantes();

    if (faltantes.length > 0) {

      this.faltantes.set(faltantes);

      return (
        'Para enviar a revisión falta: ' +
        faltantes.map(f => f.nombre).join(', ') +
        '.'
      );
    }

    return null;
  }

  /**
   * Información mínima para enviar a revisión, la misma que revisa el backend
   * (PublicacionService.camposFaltantes).
   */
  datosFaltantes(): DatoFaltante[] {

    const d = this.draft;

    const faltantes: DatoFaltante[] = [];

    const agregar = (
      falta: boolean,
      nombre: string,
      paso: NumeroPaso
    ) => {

      if (falta) {
        faltantes.push({ nombre, paso });
      }
    };

    agregar(!d.titulo.trim(), 'título', 1);
    agregar(!d.descripcion.trim(), 'descripción', 1);
    agregar(!d.ubicacion.trim(), 'ubicación', 1);
    agregar(!d.ciudad.trim(), 'ciudad', 1);
    agregar(!d.tipo.trim(), 'tipo de alojamiento', 1);
    agregar(d.capacidad === null, 'capacidad', 1);
    agregar(d.habitaciones === null, 'habitaciones', 1);
    agregar(d.banos === null, 'baños', 1);
    agregar(d.precioNoche === null, 'precio por noche', 1);
    agregar(!d.horaEntrada, 'hora de entrada', 2);
    agregar(!d.horaSalida, 'hora de salida', 2);
    agregar(!d.reglas.trim(), 'reglas', 2);
    agregar(d.idPolitica === null, 'política de cancelación', 2);

    agregar(
      this.imagenesGuardadas() === 0 &&
      this.imagenesSeleccionadas.length === 0,
      'al menos una imagen',
      4
    );

    return faltantes;
  }

  // ======================================
  // 12. CONSTRUIR DATOS PARA EL BACKEND
  // El estado lo asigna el backend (BORRADOR).
  // ======================================

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

      amenidades: [
        ...d.amenidades
      ]
    };
  }

  // ======================================
  // 13. MENSAJES DE ERROR
  // ======================================

  private mensajeError(
    err: unknown
  ): string {

    if (err instanceof HttpErrorResponse) {

      if (err.status === 0) {

        return (
          'No se pudo conectar con Spring Boot ' +
          'en localhost:8081.'
        );
      }

      if (err.status === 401) {

        return (
          'Tu sesión expiró. ' +
          'Inicia sesión nuevamente.'
        );
      }

      if (err.status === 403) {

        return (
          'No tienes permisos para realizar ' +
          'esta operación como anfitrión.'
        );
      }

      if (
        err.error &&
        typeof err.error.message === 'string'
      ) {

        return err.error.message;
      }

      return (
        `Ocurrió un error en el servidor ` +
        `(HTTP ${err.status}).`
      );
    }

    return (
      'No se pudo completar la operación. ' +
      'Revisa la conexión e inténtalo nuevamente.'
    );
  }
}
