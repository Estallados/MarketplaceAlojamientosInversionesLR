import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ConsultarAuditoria } from './consultar-auditoria';
import { AuditoriaService } from '../services/auditoria.service';
import { AdminUsuariosService } from '../../administracion/services/admin-usuarios.service';
import { EventoAuditoria, PaginaAuditoria } from '../models/evento-auditoria';
import { routes } from '../../app.routes';

/**
 * HU-13 Consultar eventos de auditoría (SCRUM-270).
 */
describe('ConsultarAuditoria (HU-13)', () => {
  let servicio: {
    pagina: ReturnType<typeof vi.fn>;
    evento: ReturnType<typeof vi.fn>;
    mensajeDeError: ReturnType<typeof vi.fn>;
  };

  const login: EventoAuditoria = {
    idEvento: 7,
    accion: 'LOGIN',
    fecha: '2026-11-10T08:30:00',
    idUsuario: 3,
    entidad: 'USUARIO',
    idEntidad: 3,
    resultado: 'EXITOSO',
  };
  const denegado: EventoAuditoria = {
    idEvento: 6,
    accion: 'ACCESO_DENEGADO',
    fecha: '2026-11-09T21:00:00',
    detalle: 'GET /admin/auditoria',
    idUsuario: null,
    resultado: 'RECHAZADO',
  };
  const pagina: PaginaAuditoria = { contenido: [login, denegado], pagina: 0, tamanio: 20, totalElementos: 2, totalPaginas: 1 };

  function crear() {
    TestBed.configureTestingModule({
      imports: [ConsultarAuditoria],
      providers: [
        { provide: AuditoriaService, useValue: servicio },
        {
          provide: AdminUsuariosService,
          useValue: { usuarios: () => of([{ idUsuario: 3, nombre: 'Ana', correo: 'a@x.co', rol: 'HUESPED', estado: 'ACTIVO' }]) },
        },
      ],
    });
    const c = TestBed.createComponent(ConsultarAuditoria).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    servicio = {
      pagina: vi.fn().mockReturnValue(of(pagina)),
      evento: vi.fn().mockReturnValue(of({ ...login, detalle: 'Inicio de sesión correcto' })),
      mensajeDeError: vi.fn((e: HttpErrorResponse, defecto: string) =>
        e.status === 403 ? 'No tienes permisos para consultar la auditoría.' : defecto,
      ),
    };
  });

  it('CA-062: lista los eventos con usuario, acción, entidad y resultado', () => {
    const c = crear();

    expect(servicio.pagina).toHaveBeenCalledWith(0, 20, {
      accion: undefined,
      idActor: undefined,
      desde: undefined,
      hasta: undefined,
    });
    expect(c.auditEvents().length).toBe(2);
    expect(c.usuario(login)).toBe('Ana (#3)');
    expect(c.usuario(denegado)).toBe('Sistema');
    expect(c.entidad(login)).toBe('USUARIO #3');
    expect(c.resultado(denegado)).toBe('Rechazado');
  });

  it('CA-062: filtra por acción, actor y rango de fechas', () => {
    const c = crear();
    c.accion.set(' login ');
    c.idActor.set('3');
    c.desde.set('2026-11-01');
    c.hasta.set('2026-11-10');

    c.filtrar();

    expect(servicio.pagina).toHaveBeenLastCalledWith(0, 20, {
      accion: 'LOGIN',
      idActor: 3,
      desde: '2026-11-01T00:00:00',
      hasta: '2026-11-10T23:59:59',
    });
  });

  it('CA-062: valida el rango de fechas antes de consultar', () => {
    const c = crear();
    servicio.pagina.mockClear();
    c.desde.set('2026-11-10');
    c.hasta.set('2026-11-01');

    c.filtrar();

    expect(c.errorFiltro()).toContain('Desde');
    expect(servicio.pagina).not.toHaveBeenCalled();
  });

  it('CA-062: muestra el detalle de un evento', () => {
    const c = crear();

    c.verDetalle(login);

    expect(servicio.evento).toHaveBeenCalledWith(7);
    expect(c.detalleAbierto()).toBe(true);
    expect(c.camposDetalle().find((f) => f.clave === 'Detalle')?.valor).toBe('Inicio de sesión correcto');
  });

  it('CA-065: sin permisos administrativos no se muestra la auditoría', () => {
    const ruta = routes.find((r) => r.path === 'admin/auditoria');
    expect(ruta?.data?.['roles']).toEqual(['Administrador']);
    expect(ruta?.canActivate?.length).toBe(1);

    servicio.pagina.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const c = crear();

    expect(c.auditEvents()).toEqual([]);
    expect(c.errorCarga()).toBe('No tienes permisos para consultar la auditoría.');
  });
});
