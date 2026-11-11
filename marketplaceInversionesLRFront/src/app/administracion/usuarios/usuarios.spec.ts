import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { Usuarios } from './usuarios';
import { AdminUsuariosService } from '../services/admin-usuarios.service';
import { UsuarioAdmin } from '../models/usuario-admin';

/**
 * HU-12 Restringir cuentas de usuarios (SCRUM-268).
 */
describe('Usuarios (HU-12)', () => {
  let componente: Usuarios;
  let servicio: {
    usuarios: ReturnType<typeof vi.fn>;
    restringir: ReturnType<typeof vi.fn>;
    liberar: ReturnType<typeof vi.fn>;
    mensajeDeError: ReturnType<typeof vi.fn>;
  };

  const ana: UsuarioAdmin = { idUsuario: 2, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', estado: 'ACTIVO' };
  const luis: UsuarioAdmin = {
    idUsuario: 3,
    nombre: 'Luis',
    correo: 'luis@correo.com',
    rol: 'ANFITRION',
    estado: 'RESTRINGIDO',
    motivoRestriccion: 'Publicaciones falsas',
  };
  const admin: UsuarioAdmin = { idUsuario: 1, nombre: 'Admin', correo: 'admin@correo.com', rol: 'ADMIN', estado: 'ACTIVO' };

  beforeEach(() => {
    servicio = {
      usuarios: vi.fn().mockReturnValue(of([admin, ana, luis])),
      restringir: vi.fn().mockReturnValue(of({ ...ana, estado: 'RESTRINGIDO' })),
      liberar: vi.fn().mockReturnValue(of({ ...luis, estado: 'ACTIVO' })),
      mensajeDeError: vi.fn((e: HttpErrorResponse, defecto: string) => e.error?.mensaje ?? defecto),
    };
    TestBed.configureTestingModule({
      imports: [Usuarios],
      providers: [provideRouter([]), { provide: AdminUsuariosService, useValue: servicio }],
    });
    componente = TestBed.createComponent(Usuarios).componentInstance;
    componente.ngOnInit();
  });

  it('CA-058: restringe la cuenta con motivo obligatorio y muestra el nuevo estado', () => {
    componente.pedirAccion(ana);
    expect(componente.accion()?.tipo).toBe('restringir');

    componente.confirmarAccion();
    expect(componente.puedeConfirmar()).toBe(false);
    expect(servicio.restringir).not.toHaveBeenCalled();

    componente.motivo.set('  Suplantación de identidad  ');
    componente.confirmarAccion();

    expect(servicio.restringir).toHaveBeenCalledWith(2, 'Suplantación de identidad');
    expect(componente.mensaje()).toContain('restringida');
    expect(componente.accion()).toBeNull();
    expect(servicio.usuarios).toHaveBeenCalledTimes(2);
  });

  it('CA-058: muestra el estado y el motivo de las cuentas restringidas', () => {
    expect(componente.estado(luis)).toBe('Restringido');
    expect(componente.esRestringido(luis)).toBe(true);
    expect(componente.restringidos()).toBe(1);

    componente.filtroEstado.set('RESTRINGIDO');
    expect(componente.visibles()).toEqual([luis]);
  });

  it('CA-059: una cuenta restringida solo se puede activar y un administrador no se puede restringir', () => {
    componente.pedirAccion(luis);
    expect(componente.accion()?.tipo).toBe('activar');
    componente.confirmarAccion();
    expect(servicio.liberar).toHaveBeenCalledWith(3, undefined);

    expect(componente.esAdmin(admin)).toBe(true);
  });

  it('muestra el mensaje del backend si la acción falla', () => {
    servicio.restringir.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { mensaje: 'No se puede restringir a un administrador.' } })),
    );
    componente.pedirAccion(ana);
    componente.motivo.set('Prueba');
    componente.confirmarAccion();

    expect(componente.errorAccion()).toBe('No se puede restringir a un administrador.');
    expect(componente.accion()).not.toBeNull();
  });
});
