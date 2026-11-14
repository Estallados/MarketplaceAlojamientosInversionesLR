import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { DashboardAdmin } from './dashboard';
import { API_URL } from '../../core/services/api-url';
import { Dashboard } from '../models/dashboard';
import { routes } from '../../app.routes';

/**
 * HU-04 Consultar dashboard de indicadores (SCRUM-258).
 */
describe('DashboardAdmin (HU-04)', () => {
  let http: HttpTestingController;

  const datos: Dashboard = {
    totalUsuarios: 1250,
    alojamientos: { total: 40, porEstado: { BORRADOR: 3, PENDIENTE_REVISION: 2, ACTIVA: 30, PAUSADA: 4, BLOQUEADA: 1 } },
    reservas: {
      total: 20,
      porEstado: { PENDIENTE_PAGO: 1, CONFIRMADA: 14, EN_REVISION: 1, CANCELADA: 4 },
      confirmadasFinalizadas: 6,
    },
    ingresosEstimados: 3450000,
    ciudadesTop: [
      { ciudad: 'Cartagena', totalReservas: 9 },
      { ciudad: 'Bogotá', totalReservas: 1 },
    ],
  };

  function crear() {
    TestBed.configureTestingModule({
      imports: [DashboardAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: 'http://api' }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DashboardAdmin);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('CA-022: muestra usuarios, publicaciones activas, reservas, ingresos y ciudades con más demanda', () => {
    const fixture = crear();
    const req = http.expectOne((r) => r.url === 'http://api/admin/dashboard');
    expect(req.request.params.get('topCiudades')).toBe('5');
    req.flush(datos);
    fixture.detectChanges();

    const c = fixture.componentInstance;
    const metricas = Object.fromEntries(c.metrics().map((m) => [m.label, m]));
    expect(metricas['Usuarios registrados'].value).toBe('1.250');
    expect(metricas['Alojamientos'].delta).toBe('30 activos');
    expect(metricas['Reservas'].value).toBe('20');
    expect(metricas['Ingresos estimados'].value.replace(/\s/g, ' ')).toContain('3.450.000');
    expect(metricas['Alojamientos por revisar'].value).toBe('2');

    expect(c.reservationStats().map((s) => [s.label, s.value])).toEqual([
      ['Confirmadas', 8],
      ['Pendientes', 2],
      ['Finalizadas', 6],
      ['Canceladas', 4],
    ]);
    expect(c.topCities()).toEqual([
      { city: 'Cartagena', total: 9 },
      { city: 'Bogotá', total: 1 },
    ]);
    expect(fixture.nativeElement.textContent).toContain('Cartagena');
  });

  it('CA-022: solo el administrador puede entrar y se muestra el error si el back lo rechaza', () => {
    const ruta = routes.find((r) => r.path === 'admin/dashboard');
    expect(ruta?.data?.['roles']).toEqual(['Administrador']);

    const fixture = crear();
    http.expectOne((r) => r.url === 'http://api/admin/dashboard').flush(null, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.componentInstance.errorCarga()).toBe('No tienes permisos para consultar el dashboard.');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });
});
