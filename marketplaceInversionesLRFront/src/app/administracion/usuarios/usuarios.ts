import { DatePipe } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { UsuarioAdmin } from '../models/usuario-admin';
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
 * Administración de usuarios (HU-12): lista con filtros, estado visible y acción de restringir
 * (con motivo obligatorio) o activar la cuenta.
 */
@Component({
  selector: 'app-admin-usuarios',
  standalone: true,
  templateUrl: './usuarios.html',
  imports: [DatePipe],
})
export class Usuarios implements OnInit {
  private readonly router = inject(Router);
  private readonly adminService = inject(AdminUsuariosService);

  // ==================== DATOS ====================
  readonly encabezados = ['Nombre', 'Correo electrónico', 'Rol', 'Estado', 'Fecha de registro', 'Acciones'];
  readonly maxMotivo = MAX_MOTIVO_USUARIO;
  readonly usuarios = signal<UsuarioAdmin[]>([]);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);
  readonly mensaje = signal('');

  // ==================== FILTROS ====================
  readonly busqueda = signal('');
  readonly filtroRol = signal('Todos');
  readonly filtroEstado = signal('Todos');

  readonly visibles = computed(() => {
    const termino = this.busqueda().trim().toLowerCase();
    const rol = this.filtroRol();
    const estado = this.filtroEstado();
    return this.usuarios().filter(
      (u) =>
        `${u.nombre ?? ''} ${u.correo ?? ''}`.toLowerCase().includes(termino) &&
        (rol === 'Todos' || tipoRol(u.rol) === rol) &&
        (estado === 'Todos' || tipoEstado(u.estado) === estado),
    );
  });

  readonly restringidos = computed(() => this.usuarios().filter((u) => tipoEstado(u.estado) === 'RESTRINGIDO').length);

  onBusquedaChange(event: Event): void {
    this.busqueda.set((event.target as HTMLInputElement).value);
  }

  onRolChange(event: Event): void {
    this.filtroRol.set((event.target as HTMLSelectElement).value);
  }

  onEstadoChange(event: Event): void {
    this.filtroEstado.set((event.target as HTMLSelectElement).value);
  }

  limpiarFiltros(): void {
    this.busqueda.set('');
    this.filtroRol.set('Todos');
    this.filtroEstado.set('Todos');
  }

  // ==================== INICIALIZACIÓN ====================
  ngOnInit(): void {
    this.cargarUsuarios();
  }

  reintentar(): void {
    this.cargarUsuarios();
  }

  /** Si `silencioso` es true no muestra el esqueleto de carga (se usa al refrescar tras una acción). */
  private cargarUsuarios(silencioso = false): void {
    if (!silencioso) {
      this.loading.set(true);
    }
    this.errorCarga.set(false);
    this.adminService.usuarios().subscribe({
      next: (respuesta) => {
        this.usuarios.set(respuesta ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.usuarios.set([]);
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }

  // ==================== ROL Y ESTADO ====================
  esActivo(usuario: UsuarioAdmin): boolean {
    return tipoEstado(usuario.estado) === 'ACTIVO';
  }

  esRestringido(usuario: UsuarioAdmin): boolean {
    return tipoEstado(usuario.estado) === 'RESTRINGIDO';
  }

  /** A un administrador no se le puede restringir. */
  esAdmin(usuario: UsuarioAdmin): boolean {
    return tipoRol(usuario.rol) === 'ADMIN';
  }

  rol(usuario: UsuarioAdmin): string {
    return etiquetaRol(usuario.rol);
  }

  estado(usuario: UsuarioAdmin): string {
    return etiquetaEstado(usuario.estado);
  }

  claseEstado(usuario: UsuarioAdmin): string {
    return claseEstado(usuario.estado);
  }

  // ==================== NAVEGACIÓN ====================
  abrirUsuario(id: number | undefined): void {
    if (id != null) {
      void this.router.navigate(['/admin/usuarios', id]);
    }
  }

  // ==================== MODAL DE CONFIRMACIÓN ====================
  readonly accion = signal<{ tipo: TipoAccionUsuario; usuario: UsuarioAdmin } | null>(null);
  readonly motivo = signal('');
  readonly guardando = signal(false);
  readonly errorAccion = signal('');

  readonly modal = computed(() => {
    const a = this.accion();
    return a ? ACCIONES_USUARIO[a.tipo] : null;
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

  pedirAccion(usuario: UsuarioAdmin): void {
    this.motivo.set('');
    this.errorAccion.set('');
    this.mensaje.set('');
    this.accion.set({ tipo: this.esActivo(usuario) ? 'restringir' : 'activar', usuario });
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
    const a = this.accion();
    if (!a || !this.puedeConfirmar()) {
      return;
    }
    const motivo = this.motivo().trim();
    const peticion =
      a.tipo === 'restringir'
        ? this.adminService.restringir(a.usuario.idUsuario, motivo)
        : this.adminService.liberar(a.usuario.idUsuario, motivo || undefined);

    this.guardando.set(true);
    this.errorAccion.set('');
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.accion.set(null);
        this.mensaje.set(
          a.tipo === 'restringir'
            ? `La cuenta de ${a.usuario.nombre} fue restringida. Ya no puede iniciar sesión.`
            : `La cuenta de ${a.usuario.nombre} fue activada.`,
        );
        // Recarga la lista para mostrar el estado real guardado por el backend.
        this.cargarUsuarios(true);
      },
      error: (error: unknown) => {
        this.errorAccion.set(this.adminService.mensajeDeError(error, 'No se pudo completar la acción.'));
        this.guardando.set(false);
      },
    });
  }
}
