import { Routes } from '@angular/router';
import { Register } from './auth/register/register';
import { Login } from './auth/login/login';
import { Recover } from './auth/recover/recover';
import { Tarifas } from './cotizacion/tarifas/tarifas';
import { roleGuard } from './core/guards/role.guard';
import { CrearPublicacion } from './alojamientos/crear-publicacion/crear-publicacion';
import { ProfileView } from './perfil/profile-view/profile-view';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: Login },
  { path: 'registro', component: Register },



  { path: 'recuperar-contrasena', component: Recover },



  { path: 'restablecer-contrasena', component: Recover },



  {
    path: 'perfil',
    component: ProfileView,
    canActivate: [roleGuard]
  },



  {
    path: 'host/tarifas',
    component: Tarifas,
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  {
    path: 'admin/tarifas',
    component: Tarifas,
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },



  // ====================================
  // MIS ALOJAMIENTOS (HU-19)
  // SOLO ANFITRIÓN: GET /alojamientos/mios exige el rol ANFITRION.
  // ====================================

  {
    path: 'host/alojamientos',
    loadComponent: () =>
      import('./alojamientos/mis-alojamientos/mis-alojamientos')
        .then(m => m.MisAlojamientos),
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  {
    path: 'host/alojamientos/crear',
    component: CrearPublicacion,
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },



  {
    path: 'host/alojamientos/:id/disponibilidad',
    loadComponent: () =>
      import('./alojamientos/availability/availability')
        .then(m => m.Availability),
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },



  {
    path: 'host/alojamientos/:id/editar',
    loadComponent: () =>
      import('./alojamientos/editar-publicacion/editar-publicacion')
        .then(m => m.EditarPublicacion),
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  {
    path: 'admin/alojamientos/:id/editar',
    loadComponent: () =>
      import('./alojamientos/editar-publicacion/editar-publicacion')
        .then(m => m.EditarPublicacion),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },



  {
    path: 'admin/alojamientos',
    loadComponent: () =>
      import('./administracion/revisar-publicaciones/revisar-publicaciones')
        .then(m => m.RevisarPublicaciones),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },

  {
    path: 'admin/alojamientos/:id',
    loadComponent: () =>
      import('./administracion/detalle-publicacion/detalle-publicacion')
        .then(m => m.DetallePublicacion),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },

  // ====================================
  // RESTRINGIR CUENTAS DE USUARIOS (HU-12)
  // SOLO ADMINISTRADOR. Carga diferida para no aumentar el bundle inicial.
  // ====================================

  {
    path: 'admin/usuarios',
    loadComponent: () =>
      import('./administracion/usuarios/usuarios')
        .then(m => m.Usuarios),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },

  {
    path: 'admin/usuarios/:id',
    loadComponent: () =>
      import('./administracion/detalle-usuario/detalle-usuario')
        .then(m => m.DetalleUsuario),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },

  // ====================================
  // RESERVAS EN REVISIÓN POR BLOQUEO (HU-11)
  // SOLO ADMINISTRADOR. Carga diferida para no aumentar el bundle inicial.
  // ====================================

  {
    path: 'admin/reservas',
    loadComponent: () =>
      import('./administracion/reservas/reservas')
        .then(m => m.ReservasAdmin),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },

  // ====================================
  // CONSULTAR EVENTOS DE AUDITORÍA (HU-13)
  // SOLO ADMINISTRADOR: GET /admin/auditoria exige el rol ADMIN.
  // ====================================

  {
    path: 'admin/auditoria',
    loadComponent: () =>
      import('./auditoria/consultar-auditoria/consultar-auditoria')
        .then(m => m.ConsultarAuditoria),
    canActivate: [roleGuard],
    data: { roles: ['Administrador'] }
  },



  // ====================================
  // BUSCAR ALOJAMIENTOS CON FILTROS (HU-23)
  // PÚBLICA. Carga diferida para no aumentar el bundle inicial.
  // ====================================

  {
    path: 'explorar',
    loadComponent: () =>
      import('./busqueda/buscar-alojamientos/buscar-alojamientos')
        .then(m => m.BuscarAlojamientos)
  },

  // ====================================
  // DETALLE DEL ALOJAMIENTO CON MAPA (HU-18)
  // PÚBLICA. Carga diferida para no aumentar el bundle inicial.
  // ====================================

  {
    path: 'alojamientos/:id',
    loadComponent: () =>
      import('./alojamientos/detalle-alojamiento/detalle-alojamiento')
        .then(m => m.DetalleAlojamientoPage)
  },

  {
    path: 'alojamientos/:id/consultar-disponibilidad',
    loadComponent: () =>
      import('./disponibilidad/consultar-disponibilidad/consultar-disponibilidad')
        .then(m => m.ConsultarDisponibilidad)
  },

  // ====================================
  // CREAR RESERVA CON BLOQUEO AUTOMÁTICO DE FECHAS (HU-22)
  // SOLO HUÉSPED: POST /reservas exige el rol HUESPED.
  // Recibe ?llegada=&salida= (y opcional &huespedes=) desde HU-21.
  // Carga diferida para no aumentar el bundle inicial.
  // ====================================

  {
    path: 'alojamientos/:id/reservar',
    loadComponent: () =>
      import('./reservas/confirmar-reserva/confirmar-reserva')
        .then(m => m.ConfirmarReserva),
    canActivate: [roleGuard],
    data: { roles: ['Huésped'] }
  },

  // ====================================
  // ALOJAMIENTOS FAVORITOS (HU-29)
  // SOLO HUÉSPED. Carga diferida para no aumentar el bundle inicial.
  // ====================================

  {
    path: 'favoritos',
    loadComponent: () =>
      import('./favoritos/mis-favoritos/mis-favoritos')
        .then(m => m.MisFavoritos),
    canActivate: [roleGuard],
    data: { roles: ['Huésped'] }
  },

  // ====================================
  // ESTADO E HISTORIAL DE RESERVAS (HU-25)
  // /reservas y /reservas/:id: solo huésped (GET /reservas/mias, GET /reservas/{id}).
  // /host/reservas y /host/reservas/:id: solo anfitrión (GET /reservas/anfitrion, GET /reservas/{id}).
  // ====================================

  {
    path: 'reservas',
    loadComponent: () =>
      import('./reservas/mis-reservas/mis-reservas')
        .then(m => m.MisReservas),
    canActivate: [roleGuard],
    data: { roles: ['Huésped'] }
  },

  {
    path: 'reservas/:id',
    loadComponent: () =>
      import('./reservas/detalle-reserva/detalle-reserva')
        .then(m => m.DetalleReserva),
    canActivate: [roleGuard],
    data: { roles: ['Huésped'] }
  },

  {
    path: 'host/reservas',
    loadComponent: () =>
      import('./reservas/reservas-anfitrion/reservas-anfitrion')
        .then(m => m.ReservasAnfitrion),
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  {
    path: 'host/reservas/:id',
    loadComponent: () =>
      import('./reservas/detalle-reserva-anfitrion/detalle-reserva-anfitrion')
        .then(m => m.DetalleReservaAnfitrion),
    canActivate: [roleGuard],
    data: { roles: ['Anfitrión'] }
  },

  // ====================================
  // RECIBIR Y CONSULTAR NOTIFICACIONES (HU-02)
  // CUALQUIER USUARIO CON SESIÓN: cada uno ve solo las suyas.
  // ====================================

  {
    path: 'notificaciones',
    loadComponent: () =>
      import('./notificaciones/mis-notificaciones/mis-notificaciones')
        .then(m => m.MisNotificaciones),
    canActivate: [roleGuard]
  },

  { path: '**', redirectTo: 'login' }
];
