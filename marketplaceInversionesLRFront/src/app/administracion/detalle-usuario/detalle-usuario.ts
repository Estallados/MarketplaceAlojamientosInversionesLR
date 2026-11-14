import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { AlojamientoDeUsuario, UsuarioAdmin } from '../models/usuario-admin';
import {
  ACCIONES_USUARIO,
  AdminUsuariosService,
  MAX_MOTIVO_USUARIO,
  TipoAccionUsuario,
  claseEstado,
  etiquetaEstado,
  etiquetaRol,
  tipoEstado,
  tipoRol,
} from '../services/admin-usuarios.service';

/**
 * Detalle de un usuario para el administrador (HU-12): información, estado y motivo de la
 * restricción, alojamientos o reservas según el rol, y acción de restringir o activar.
 */
@Component({
  selector: 'app-admin-detalle-usuario',
  standalone: true,
  templateUrl: './detalle-usuario.html',
})
export class DetalleUsuario implements OnInit {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly adminService = inject(AdminUsuariosService);

  // ==================== Usuario ====================
  readonly usuario = signal<UsuarioAdmin | null>(null);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);
  readonly mensajeError = signal('');
  readonly mensaje = signal('');
  readonly maxMotivo = MAX_MOTIVO_USUARIO;

  // Solo para anfitriones
  readonly alojamientos = signal<AlojamientoDeUsuario[]>([]);
  // Solo para huéspedes
  readonly totalReservas = signal(0);
  readonly estanciasFinalizadas = signal(0);

  private idUsuario = 0;

  // ==================== Rol y estado ====================
  readonly rol = computed(() => tipoRol(this.usuario()?.rol));
  readonly estado = computed(() => tipoEstado(this.usuario()?.estado));
  readonly activo = computed(() => this.estado() === 'ACTIVO');
  readonly restringido = computed(() => this.estado() === 'RESTRINGIDO');
  readonly etiquetaRol = computed(() => etiquetaRol(this.usuario()?.rol));
  readonly etiquetaEstado = computed(() => etiquetaEstado(this.usuario()?.estado));
  readonly claseEstado = computed(() => claseEstado(this.usuario()?.estado));

  claseAlojamiento(estado: unknown): string {
    const e = String(estado ?? '').toUpperCase();
    if (e.includes('BLOQUE')) return 'bg-red-50 text-red-700';
    if (e.includes('PAUSAD') || e.includes('PENDIENTE')) return 'bg-amber-50 text-amber-700';
    if (e.includes('ACTIV')) return 'bg-brand-50 text-brand-700';
    return 'bg-ink-100 text-ink-600';
  }

  // ==================== Información visual ====================
  readonly informacion = computed(() => {
    const u = this.usuario();
    if (!u) {
      return [];
    }
    return [
      { label: 'Nombre', value: u.nombre },
      { label: 'Correo electrónico', value: u.correo },
      { label: 'Rol', value: this.etiquetaRol() },
      { label: 'Estado de la cuenta', value: this.etiquetaEstado() },
      {
        label: 'Fecha de registro',
        value: u.fechaRegistro ? new Date(u.fechaRegistro).toLocaleDateString('es-CO') : '',
      },
      { label: 'Identificador interno', value: u.idUsuario },
    ];
  });

  readonly tituloSeccion = computed(() => {
    if (this.rol() === 'ANFITRION') return 'Alojamientos publicados';
    if (this.rol() === 'HUESPED') return 'Resumen de reservas';
    return 'Cuenta administrativa';
  });

  // ==================== Inicialización ====================
  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = Number(idParam);
    if (!idParam || !Number.isInteger(id) || id <= 0) {
      this.errorCarga.set(true);
      this.mensajeError.set('El identificador del usuario no es válido.');
      this.loading.set(false);
      return;
    }
    this.idUsuario = id;
    this.cargarUsuario();
  }

  reintentar(): void {
    this.cargarUsuario();
  }

  /** Si `silencioso` es true no muestra el indicador de carga (se usa al refrescar tras una acción). */
  private cargarUsuario(silencioso = false): void {
    if (!silencioso) {
      this.loading.set(true);
    }
    this.errorCarga.set(false);
    this.adminService.obtener(this.idUsuario).subscribe({
      next: (encontrado) => {
        if (!encontrado) {
          this.errorCarga.set(true);
          this.mensajeError.set('Usuario no encontrado.');
          this.loading.set(false);
          return;
        }
        this.usuario.set(encontrado);
        this.loading.set(false);
        if (this.rol() === 'ANFITRION') {
          this.cargarAlojamientos();
        } else if (this.rol() === 'HUESPED') {
          this.cargarReservas();
        }
      },
      error: (error: unknown) => {
        this.errorCarga.set(true);
        this.mensajeError.set(this.adminService.mensajeDeError(error, 'No se pudo cargar el usuario.'));
        this.loading.set(false);
      },
    });
  }

  private cargarAlojamientos(): void {
    this.adminService.alojamientosDeUsuario(this.idUsuario).subscribe({
      next: (lista) => this.alojamientos.set(lista ?? []),
      error: () => this.alojamientos.set([]),
    });
  }

  private cargarReservas(): void {
    const hoy = new Date().toISOString().slice(0, 10);
    this.adminService.reservasDeUsuario(this.idUsuario).subscribe({
      next: (lista) => {
        this.totalReservas.set(lista?.length ?? 0);
        // Finalizada: confirmada y con la fecha de salida ya pasada.
        this.estanciasFinalizadas.set(
          (lista ?? []).filter((r) => r.estado === 'CONFIRMADA' && !!r.fechaSalida && r.fechaSalida < hoy).length,
        );
      },
      error: () => {
        this.totalReservas.set(0);
        this.estanciasFinalizadas.set(0);
      },
    });
  }

  // ==================== Navegación ====================
  volver(): void {
    void this.router.navigateByUrl('/admin/usuarios');
  }

  // ==================== Modal de confirmación ====================
  readonly accion = signal<TipoAccionUsuario | null>(null);
  readonly motivo = signal('');
  readonly guardando = signal(false);
  readonly errorAccion = signal('');

  readonly modal = computed(() => {
    const a = this.accion();
    return a ? ACCIONES_USUARIO[a] : null;
  });

  readonly puedeConfirmar = computed(() => {
    const m = this.modal();
    const largo = this.motivo().trim().length;
    return !!m && !this.guardando() && (!m.necesitaMotivo || (largo > 0 && largo <= MAX_MOTIVO_USUARIO));
  });

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.cerrarAccion();
  }

  pedirAccion(): void {
    this.motivo.set('');
    this.errorAccion.set('');
    this.mensaje.set('');
    this.accion.set(this.activo() ? 'restringir' : 'activar');
  }

  onMotivoChange(event: Event): void {
    this.motivo.set((event.target as HTMLTextAreaElement).value);
  }

  cerrarAccion(): void {
    if (!this.guardando()) {
      this.accion.set(null);
    }
  }

  confirmarAccion(): void {
    const tipo = this.accion();
    if (!tipo || !this.puedeConfirmar()) {
      return;
    }
    const motivo = this.motivo().trim();
    const peticion =
      tipo === 'restringir'
        ? this.adminService.restringir(this.idUsuario, motivo)
        : this.adminService.liberar(this.idUsuario, motivo || undefined);

    this.guardando.set(true);
    this.errorAccion.set('');
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.accion.set(null);
        this.mensaje.set(
          tipo === 'restringir'
            ? 'La cuenta fue restringida. El usuario ya no puede iniciar sesión.'
            : 'La cuenta fue activada.',
        );
        // Recarga para mostrar el estado real guardado por el backend.
        this.cargarUsuario(true);
      },
      error: (error: unknown) => {
        this.errorAccion.set(this.adminService.mensajeDeError(error, 'No se pudo completar la acción.'));
        this.guardando.set(false);
      },
    });
  }
}
