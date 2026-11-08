
import {
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';

import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { finalize, timeout } from 'rxjs';

import {
  User,
  UsuarioDTO
} from '../../../services/user';

export interface Profile {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  registered: string;
  active: boolean;
}

type EditableKey = 'firstName' | 'phone';

@Component({
  selector: 'app-profile-view',
  standalone: true,
  templateUrl: './profile-view.html'
})
export class ProfileView implements OnInit {

  // ======================================
  // SERVICIOS
  // ======================================

  private readonly usuarioService = inject(User);
  private readonly router = inject(Router);

  // ======================================
  // DATOS DE LA SESIÓN
  // ======================================

  private readonly correoSesion =
    sessionStorage.getItem('correo') ?? '';

  private readonly rolSesion =
    sessionStorage.getItem('rol') ?? '';

  readonly role = this.normalizarRol(this.rolSesion);

  // ======================================
  // PERFIL DEL USUARIO
  // ======================================

  readonly profile = signal<Profile>({
    firstName: '',
    lastName: '',
    email: this.correoSesion,
    phone: '',
    registered: '',
    active: false
  });

  readonly form = signal<Profile>({
    ...this.profile()
  });

  readonly editing = signal(false);
  readonly confirmAccount = signal(false);

  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly desactivando = signal(false);

  readonly estadoConsultado = signal(false);

  readonly mensaje = signal('');
  readonly error = signal('');

  // ======================================
  // CAMPOS EDITABLES
  // ======================================

  readonly editFields: {
    label: string;
    key: EditableKey;
  }[] = [
    {
      label: 'Nombre',
      key: 'firstName'
    },
    {
      label: 'Teléfono',
      key: 'phone'
    }
  ];

  // ======================================
  // INICIALIZACIÓN
  // ======================================

  ngOnInit(): void {
    this.cargarPerfil();
  }

  // ======================================
  // 1. CONSULTAR PERFIL REAL
  // ======================================

  cargarPerfil(): void {

    const token = sessionStorage.getItem('token');

    if (!token) {
      this.router.navigateByUrl('/login');
      return;
    }

    this.cargando.set(true);
    this.error.set('');

    this.usuarioService.obtenerMiPerfil()
      .pipe(
        timeout(15000),
        finalize(() => {
          this.cargando.set(false);
        })
      )
      .subscribe({

        next: (usuario: UsuarioDTO) => {

          console.log('Datos del perfil recibidos:', usuario);

          const nombre = usuario.nombre ?? '';
          const apellido = usuario.apellido ?? '';

          const estado =
            usuario.estado ??
            usuario.estadoUsuario;

          const estadoDisponible =
            typeof usuario.activo === 'boolean' ||
            typeof estado === 'string';

          const activo =
            typeof usuario.activo === 'boolean'
              ? usuario.activo
              : estado?.toUpperCase() === 'ACTIVO';

          this.profile.set({
            firstName: nombre,
            lastName: apellido,
            email:
              usuario.correo ??
              this.correoSesion,
            phone: usuario.telefono ?? '',
            registered:
              usuario.fechaRegistro ??
              usuario.fechaCreacion ??
              '',
            active: activo
          });

          this.form.set({
            ...this.profile()
          });

          this.estadoConsultado.set(estadoDisponible);

          if (usuario.correo) {
            sessionStorage.setItem(
              'correo',
              usuario.correo
            );
          }

          console.log('Perfil cargado correctamente');
        },

        error: (err: unknown) => {

          console.error('Error cargando perfil:', err);

          this.error.set(
            this.obtenerMensajeError(
              err,
              'No se pudieron cargar los datos del perfil.'
            )
          );
        }
      });
  }

  // ======================================
  // 2. VALIDAR FORMULARIO
  // ======================================

  readonly valid = computed(() => {

    const datos = this.form();

    const nombreValido =
      datos.firstName.trim().length > 0;

    const telefonoLimpio =
      datos.phone.replace(/\D/g, '');

    const telefonoValido =
      telefonoLimpio.length >= 7 &&
      telefonoLimpio.length <= 15;

    return nombreValido && telefonoValido;
  });

  // ======================================
  // 3. NOMBRE VISIBLE
  // ======================================

  readonly nombreVisible = computed(() => {

    const usuario = this.profile();

    const nombreCompleto = [
      usuario.firstName,
      usuario.lastName
    ]
      .filter(Boolean)
      .join(' ')
      .trim();

    if (nombreCompleto) {
      return nombreCompleto;
    }

    return usuario.email.split('@')[0] || 'Usuario';
  });

  // ======================================
  // 4. INICIALES
  // ======================================

  readonly initials = computed(() => {

    const usuario = this.profile();

    const nombre = usuario.firstName.trim();
    const apellido = usuario.lastName.trim();

    if (nombre) {

      const partes = nombre.split(/\s+/);

      const primeraInicial =
        partes[0]?.charAt(0) ?? '';

      const segundaInicial =
        apellido.charAt(0) ||
        partes[1]?.charAt(0) ||
        '';

      return (
        primeraInicial + segundaInicial
      ).toUpperCase();
    }

    return usuario.email.charAt(0).toUpperCase() || 'U';
  });

  // ======================================
  // 5. DETALLES DEL PERFIL
  // ======================================

  readonly details = computed(() => {

    const usuario = this.profile();

    return [
      {
        label: 'Nombre',
        value: usuario.firstName || 'Sin información'
      },

      {
        label: 'Correo electrónico',
        value: usuario.email || 'Sin información'
      },
      {
        label: 'Teléfono',
        value: usuario.phone || 'Sin información'
      },
      {
        label: 'Rol',
        value: this.role
      },
      {
        label: 'Fecha de registro',
        value: usuario.registered || 'Sin información'
      }
    ];
  });

  // ======================================
  // 6. EDITAR PERFIL
  // ======================================

  startEditing(): void {

    this.form.set({
      ...this.profile()
    });

    this.error.set('');
    this.mensaje.set('');
    this.editing.set(true);
  }

  // ======================================
  // 7. ACTUALIZAR CAMPO
  // ======================================

  setField(
    key: EditableKey,
    event: Event
  ): void {

    const valor =
      (event.target as HTMLInputElement).value;

    this.form.update(actual => ({
      ...actual,
      [key]: valor
    }));
  }

  // ======================================
  // 8. CANCELAR EDICIÓN
  // ======================================

  cancelEdit(): void {

    this.form.set({
      ...this.profile()
    });

    this.editing.set(false);
    this.error.set('');
    this.mensaje.set('');
  }

  // ======================================
  // 9. GUARDAR CAMBIOS EN SPRING BOOT
  // ======================================

  save(): void {

    if (this.guardando()) {
      return;
    }

    if (!this.valid()) {

      this.error.set(
        'Ingresa un nombre y un teléfono válido de 7 a 15 dígitos.'
      );

      return;
    }

    this.guardando.set(true);

    this.error.set('');
    this.mensaje.set('');

    const datos = this.form();

    this.usuarioService.actualizarMiPerfil(
      datos.firstName.trim(),
      datos.phone.trim()
    )
      .pipe(
        timeout(15000),
        finalize(() => {
          this.guardando.set(false);
        })
      )
      .subscribe({

        next: () => {

          console.log('Perfil actualizado correctamente');

          this.editing.set(false);

          this.mensaje.set(
            'Tu perfil se actualizó correctamente.'
          );

          // Consultar nuevamente los datos reales.
          this.cargarPerfil();
        },

        error: (err: unknown) => {

          console.error('Error actualizando perfil:', err);

          this.error.set(
            this.obtenerMensajeError(
              err,
              'No se pudieron guardar los cambios.'
            )
          );
        }
      });
  }

  // ======================================
  // 10. DESACTIVAR CUENTA
  // ======================================

  toggleAccount(): void {

    if (this.desactivando()) {
      return;
    }

    this.confirmAccount.set(false);

    if (!this.estadoConsultado()) {

      this.error.set(
        'No se puede modificar el estado porque todavía no se ha consultado.'
      );

      return;
    }

    if (!this.profile().active) {

      this.error.set(
        'La reactivación de la cuenta se realiza mediante el proceso de autenticación.'
      );

      return;
    }

    this.desactivando.set(true);

    this.error.set('');
    this.mensaje.set('');

    this.usuarioService.desactivarMiCuenta()
      .pipe(
        timeout(15000),
        finalize(() => {
          this.desactivando.set(false);
        })
      )
      .subscribe({

        next: () => {

          console.log('Cuenta desactivada correctamente');

          sessionStorage.removeItem('token');
          sessionStorage.removeItem('rol');
          sessionStorage.removeItem('correo');

          this.router.navigateByUrl('/login');
        },

        error: (err: unknown) => {

          console.error('Error desactivando cuenta:', err);

          this.error.set(
            this.obtenerMensajeError(
              err,
              'No se pudo desactivar la cuenta.'
            )
          );
        }
      });
  }

  // ======================================
  // 11. NORMALIZAR ROL
  // ======================================

  private normalizarRol(valor: string): string {

    const rol = valor
      .trim()
      .toUpperCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');

    switch (rol) {

      case 'HUESPED':
      case 'ROLE_HUESPED':
        return 'Huésped';

      case 'ANFITRION':
      case 'ROLE_ANFITRION':
        return 'Anfitrión';

      case 'ADMIN':
      case 'ROLE_ADMIN':
      case 'ADMINISTRADOR':
      case 'ROLE_ADMINISTRADOR':
        return 'Administrador';

      default:
        return 'Sin rol asignado';
    }
  }

  // ======================================
  // 12. MANEJAR ERRORES
  // ======================================

  private obtenerMensajeError(
    err: unknown,
    mensajePorDefecto: string
  ): string {

    if (err instanceof Error && err.name === 'TimeoutError') {

      return 'El servidor tardó demasiado en responder.';
    }

    if (!(err instanceof HttpErrorResponse)) {

      return mensajePorDefecto;
    }

    if (err.status === 0) {

      return 'No se pudo conectar con Spring Boot en el puerto 8081.';
    }

    if (err.status === 401) {

      return 'Tu sesión ha expirado. Inicia sesión nuevamente.';
    }

    if (err.status === 403) {

      return 'No tienes permisos para realizar esta operación.';
    }

    const datos: unknown = err.error;

    if (typeof datos === 'string') {

      try {

        const objeto: unknown = JSON.parse(datos);

        if (
          objeto !== null &&
          typeof objeto === 'object'
        ) {

          const respuesta = objeto as {
            message?: string;
            mensaje?: string;
          };

          return (
            respuesta.message ||
            respuesta.mensaje ||
            mensajePorDefecto
          );
        }

      } catch {

        return datos || mensajePorDefecto;
      }
    }

    if (
      datos !== null &&
      typeof datos === 'object'
    ) {

      const respuesta = datos as {
        message?: string;
        mensaje?: string;
      };

      return (
        respuesta.message ||
        respuesta.mensaje ||
        mensajePorDefecto
      );
    }

    return mensajePorDefecto;
  }
}
