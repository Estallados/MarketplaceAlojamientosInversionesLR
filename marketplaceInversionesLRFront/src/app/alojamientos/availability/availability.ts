
import {
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal
} from '@angular/core';

import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';

import { AlojamientoRespuesta } from '../models/alojamiento';

import {
  CalendarioDisponibilidad,
  DatosPeriodoDisponibilidad,
  PeriodoDisponibilidad
} from '../models/disponibilidad';

import { PublicacionHostService } from '../services/publicacion-host.service';
import { InsigniaEstado } from '../insignia-estado/insignia-estado';

import { API_URL } from '../../core/services/api-url';



type EstadoDia =
  | 'pasado'
  | 'reservado'
  | 'disponible'
  | 'no-ofrecido';

interface DiaCalendario {
  numero: number;
  fecha: string;
  estado: EstadoDia;
}

@Component({
  selector: 'app-host-availability',
  standalone: true,
  imports: [FormsModule, InsigniaEstado],
  templateUrl: './availability.html'
})
export class Availability implements OnInit {



  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly publicaciones = inject(PublicacionHostService);
  private readonly apiUrl = inject(API_URL);



  readonly idAlojamiento = signal(0);

  readonly alojamiento =
    signal<AlojamientoRespuesta | null>(null);

  readonly disponibilidad =
    signal<CalendarioDisponibilidad | null>(null);

  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly eliminando = signal(false);

  readonly error = signal('');
  readonly exito = signal('');

  readonly formularioAbierto = signal(false);

  readonly periodoPorEliminar =
    signal<PeriodoDisponibilidad | null>(null);

  readonly idEdicion = signal<number | null>(null);
  readonly fechaSeleccionada = signal('');

  formulario: DatosPeriodoDisponibilidad = {
    fechaInicio: '',
    fechaFin: ''
  };

  readonly diasSemana = [
    'Lun', 'Mar', 'Mié',
    'Jue', 'Vie', 'Sáb', 'Dom'
  ];

  // Fecha local para evitar desfases por UTC
  readonly hoy = this.fechaISO(new Date());

  readonly mesActual = signal(
    new Date(
      new Date().getFullYear(),
      new Date().getMonth(),
      1
    )
  );



  readonly tituloMes = computed(() => {

    const texto = new Intl.DateTimeFormat('es-CO', {
      month: 'long',
      year: 'numeric'
    }).format(this.mesActual());

    return texto.charAt(0).toUpperCase() + texto.slice(1);
  });



  readonly periodos = computed(() =>
    [...(this.disponibilidad()?.periodos ?? [])]
      .sort((a, b) =>
        a.fechaInicio.localeCompare(b.fechaInicio)
      )
  );



  readonly reservados = computed(
    () => this.disponibilidad()?.reservados ?? []
  );



  readonly imagen = computed(() => {

    const a = this.alojamiento();

    const ruta =
      a?.detalleImagenes?.find(i => i.principal)?.url
      ?? a?.detalleImagenes?.[0]?.url
      ?? a?.imagenes?.[0];

    if (!ruta) return '';

    return /^https?:\/\//i.test(ruta)
      ? ruta
      : `${this.apiUrl}${ruta.startsWith('/') ? '' : '/'}${ruta}`;
  });



  readonly calendario = computed<(DiaCalendario | null)[]>(() => {

    const mes = this.mesActual();

    const year = mes.getFullYear();
    const month = mes.getMonth();

    const primero = new Date(year, month, 1);

    const desplazamiento =
      (primero.getDay() + 6) % 7;

    const diasMes =
      new Date(year, month + 1, 0).getDate();

    const celdas: (DiaCalendario | null)[] =
      Array(desplazamiento).fill(null);

    for (let numero = 1; numero <= diasMes; numero++) {

      const fecha = this.fechaISO(
        new Date(year, month, numero)
      );

      const reservado = this.reservados().some(r =>
        fecha >= r.fechaEntrada &&
        fecha < r.fechaSalida
      );

      const ofrecido = this.periodos().some(p =>
        fecha >= p.fechaInicio &&
        fecha < p.fechaFin
      );

      const estado: EstadoDia =
        fecha < this.hoy
          ? 'pasado'
          : reservado
            ? 'reservado'
            : ofrecido
              ? 'disponible'
              : 'no-ofrecido';

      celdas.push({
        numero,
        fecha,
        estado
      });
    }

    return celdas;
  });



  readonly fechaDetalle = computed(() => {

    const fecha = this.fechaSeleccionada();

    if (!fecha) return null;

    return this.calendario().find(
      d => d?.fecha === fecha
    ) ?? null;
  });



  ngOnInit(): void {

    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {

        const id = Number(params.get('id'));

        if (!Number.isSafeInteger(id) || id <= 0) {
          this.error.set(
            'El ID del alojamiento no es válido.'
          );
          return;
        }

        this.idAlojamiento.set(id);

        this.alojamiento.set(null);
        this.disponibilidad.set(null);

        this.formularioAbierto.set(false);
        this.periodoPorEliminar.set(null);

        this.exito.set('');

        this.cargarDatos();
      });
  }



  cargarDatos(): void {

    const id = this.idAlojamiento();

    if (!id) return;

    this.cargando.set(true);
    this.error.set('');

    forkJoin({
      alojamiento:
        this.publicaciones.obtenerAlojamiento(id),

      calendario:
        this.publicaciones.obtenerDisponibilidad(id)
    })
      .pipe(
        finalize(() => this.cargando.set(false))
      )
      .subscribe({

        next: ({ alojamiento, calendario }) => {

          this.alojamiento.set(alojamiento);
          this.disponibilidad.set(calendario);
        },

        error: err => {
          this.error.set(this.mensajeError(err));
        }
      });
  }



  cambiarMes(pasos: number): void {

    const mes = this.mesActual();

    this.mesActual.set(
      new Date(
        mes.getFullYear(),
        mes.getMonth() + pasos,
        1
      )
    );

    this.fechaSeleccionada.set('');
  }

  seleccionarDia(dia: DiaCalendario): void {
    this.fechaSeleccionada.set(dia.fecha);
  }



  claseDia(dia: DiaCalendario): string {

    const estados: Record<EstadoDia, string> = {

      pasado:
        'bg-slate-50 text-slate-400 border-slate-100',

      reservado:
        'bg-red-50 text-red-700 border-red-200',

      disponible:
        'bg-emerald-50 text-emerald-800 border-emerald-200',

      'no-ofrecido':
        'bg-amber-50 text-amber-800 border-amber-200'
    };

    return `
      min-h-20 rounded-xl border p-2
      text-left transition hover:shadow-sm
      ${estados[dia.estado]}
      ${
      this.fechaSeleccionada() === dia.fecha
        ? 'ring-2 ring-emerald-600 ring-offset-2'
        : ''
    }
    `;
  }

  etiquetaDia(estado: EstadoDia): string {

    return {
      pasado: 'Pasado',
      reservado: 'Reservado',
      disponible: 'Disponible',
      'no-ofrecido': 'No habilitado'
    }[estado];
  }



  abrirCrear(): void {

    this.idEdicion.set(null);

    this.formulario = {
      fechaInicio: '',
      fechaFin: ''
    };

    this.error.set('');
    this.exito.set('');

    this.formularioAbierto.set(true);
  }



  abrirEditar(periodo: PeriodoDisponibilidad): void {

    this.idEdicion.set(periodo.idPeriodo);

    this.formulario = {
      fechaInicio: periodo.fechaInicio,
      fechaFin: periodo.fechaFin
    };

    this.error.set('');
    this.exito.set('');

    this.formularioAbierto.set(true);
  }

  cerrarFormulario(): void {

    if (!this.guardando()) {
      this.formularioAbierto.set(false);
    }
  }



  guardarPeriodo(): void {

    if (this.guardando()) return;

    const { fechaInicio, fechaFin } = this.formulario;

    if (
      !fechaInicio ||
      !fechaFin ||
      fechaFin <= fechaInicio
    ) {

      this.error.set(
        'Selecciona fechas válidas: la fecha final debe ser posterior a la inicial.'
      );
      return;
    }

    if (fechaFin <= this.hoy) {

      this.error.set(
        'El período debe incluir al menos una noche futura.'
      );
      return;
    }

    const solapa = this.periodos().some(p =>

      p.idPeriodo !== this.idEdicion() &&

      fechaInicio <= p.fechaFin &&

      fechaFin >= p.fechaInicio
    );

    if (solapa) {

      this.error.set(
        'El período se cruza o comparte límite con otro existente. Modifica el período anterior.'
      );

      return;
    }

    const id = this.idAlojamiento();
    const editando = this.idEdicion();

    this.guardando.set(true);
    this.error.set('');
    this.exito.set('');

    const datos = {
      fechaInicio,
      fechaFin
    };

    const solicitud = editando === null

      ? this.publicaciones.agregarPeriodoDisponibilidad(
        id,
        datos
      )

      : this.publicaciones.actualizarPeriodoDisponibilidad(
        id,
        editando,
        datos
      );

    solicitud
      .pipe(
        finalize(() => this.guardando.set(false))
      )
      .subscribe({

        next: () => {

          this.formularioAbierto.set(false);

          this.exito.set(
            editando === null
              ? 'Período registrado correctamente.'
              : 'Período actualizado correctamente.'
          );

          this.refrescarCalendario();
        },

        error: err => {
          this.error.set(this.mensajeError(err));
        }
      });
  }



  solicitarEliminar(periodo: PeriodoDisponibilidad): void {

    this.error.set('');

    this.periodoPorEliminar.set(periodo);
  }



  confirmarEliminar(): void {

    const periodo = this.periodoPorEliminar();

    if (!periodo || this.eliminando()) return;

    this.eliminando.set(true);
    this.error.set('');

    this.publicaciones
      .eliminarPeriodoDisponibilidad(
        this.idAlojamiento(),
        periodo.idPeriodo
      )
      .pipe(
        finalize(() => this.eliminando.set(false))
      )
      .subscribe({

        next: () => {

          this.periodoPorEliminar.set(null);

          this.exito.set(
            'Período eliminado correctamente.'
          );

          this.refrescarCalendario();
        },

        error: err => {

          this.periodoPorEliminar.set(null);

          this.error.set(
            this.mensajeError(err)
          );
        }
      });
  }

  // ======================================
  // ACTUALIZAR CALENDARIO
  // ======================================

  private refrescarCalendario(): void {

    this.publicaciones
      .obtenerDisponibilidad(this.idAlojamiento())
      .subscribe({

        next: data => {
          this.disponibilidad.set(data);
        },

        error: err => {

          this.error.set(
            `El cambio se guardó, pero no se pudo actualizar el calendario: ${this.mensajeError(err)}`
          );
        }
      });
  }

  // ======================================
  // FORMATO DE FECHAS
  // ======================================

  private fechaISO(fecha: Date): string {

    const anio = fecha.getFullYear();

    const mes = String(
      fecha.getMonth() + 1
    ).padStart(2, '0');

    const dia = String(
      fecha.getDate()
    ).padStart(2, '0');

    return `${anio}-${mes}-${dia}`;
  }

  // ======================================
  // MANEJO DE ERRORES
  // ======================================

  private mensajeError(err: unknown): string {

    if (err instanceof HttpErrorResponse) {

      if (err.status === 0) {
        return 'No se pudo conectar con el backend en localhost:8081.';
      }

      if (err.status === 401) {
        return 'La sesión expiró. Inicia sesión nuevamente.';
      }

      if (err.status === 403) {
        return 'No tienes permiso para modificar este alojamiento.';
      }

      if (err.status === 404) {
        return 'El alojamiento o período no existe.';
      }

      if (err.status === 409) {
        return this.mensajeServidor(err)
          || 'Hay un conflicto con otro período o una reserva.';
      }

      return this.mensajeServidor(err)
        || `Error HTTP ${err.status}.`;
    }

    return 'Ocurrió un error inesperado.';
  }

  private mensajeServidor(
    err: HttpErrorResponse
  ): string {

    return typeof err.error?.mensaje === 'string'
      ? err.error.mensaje

      : typeof err.error?.message === 'string'
        ? err.error.message
        : '';
  }

  // ======================================
  // NAVEGACIÓN ENTRE PÁGINAS
  // ======================================

  volver(): void {

    void this.router.navigateByUrl(
      '/host/alojamientos'
    );
  }

  verDetalles(): void {

    void this.router.navigate([
      '/host/alojamientos',
      this.idAlojamiento()
    ]);
  }

  editarAlojamiento(): void {

    void this.router.navigate([
      '/host/alojamientos',
      this.idAlojamiento(),
      'editar'
    ]);
  }
}
