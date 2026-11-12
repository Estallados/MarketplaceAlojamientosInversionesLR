import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';

import { MensajeriaService } from './mensajeria.service';
import { API_URL } from '../../core/services/api-url';
import { SessionService } from '../../core/services/session.service';
import { DetalleAlojamientoService } from '../../alojamientos/services/detalle-alojamiento.service';
import { Conversacion } from '../models/mensajeria';

/**
 * HU-28 Mensajería entre huésped y anfitrión (SCRUM-278): contrato con /conversaciones.
 */
describe('MensajeriaService (HU-28)', () => {
  let servicio: MensajeriaService;
  let http: HttpTestingController;
  let rol: 'Huésped' | 'Anfitrión' = 'Huésped';

  beforeEach(() => {
    rol = 'Huésped';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' },
        { provide: SessionService, useValue: { rol: () => rol } },
        { provide: DetalleAlojamientoService, useValue: { obtener: () => of({ titulo: 'Casa en la playa' }) } },
      ],
    });
    servicio = TestBed.inject(MensajeriaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const conversacionApi = {
    idConversacion: 5,
    fechaCreacion: '2026-11-10T10:00:00',
    idUsuario: 7,
    idAnfitrion: 2,
    idAlojamiento: 9,
    nombreUsuario: 'Ana Pérez',
    nombreAnfitrion: 'Luis Gómez',
  };

  it('CA-167: lista solo mis conversaciones con el otro participante y el título del alojamiento', () => {
    let lista: Conversacion[] = [];
    servicio.conversaciones().subscribe((r) => (lista = r));

    http.expectOne((r) => r.url === 'http://api/conversaciones').flush({ contenido: [conversacionApi] });

    expect(lista).toEqual([
      {
        id: 5,
        contacto: 'Luis Gómez',
        iniciales: 'LG',
        miId: 7,
        idAlojamiento: 9,
        alojamiento: 'Casa en la playa',
        fechaCreacion: '2026-11-10T10:00:00',
      },
    ]);
  });

  it('CA-165: identifica el remitente de cada mensaje y conserva la fecha de envío', () => {
    rol = 'Anfitrión';
    const conversacion: Conversacion = {
      id: 5,
      contacto: 'Ana Pérez',
      iniciales: 'AP',
      miId: 2,
      idAlojamiento: 9,
      alojamiento: '',
      fechaCreacion: '',
    };
    let textos: string[] = [];
    let mios: boolean[] = [];
    servicio.mensajes(conversacion).subscribe((m) => {
      textos = m.map((x) => `${x.fecha} ${x.remitente}: ${x.texto}`);
      mios = m.map((x) => x.mio);
    });

    http.expectOne((r) => r.url === 'http://api/conversaciones/5/mensajes').flush({
      contenido: [
        { idMensaje: 2, contenido: 'Sí, claro', fechaEnvio: '2026-11-10T10:05:00', idUsuario: 2, nombreUsuario: 'Luis Gómez' },
        { idMensaje: 1, contenido: '¿Hay parqueadero?', fechaEnvio: '2026-11-10T10:00:00', idUsuario: 7, nombreUsuario: 'Ana Pérez' },
      ],
      totalPaginas: 1,
    });

    expect(textos).toEqual([
      '2026-11-10T10:00:00 Ana Pérez: ¿Hay parqueadero?',
      '2026-11-10T10:05:00 Luis Gómez: Sí, claro',
    ]);
    expect(mios).toEqual([false, true]);
  });

  it('CA-169: inicia la conversación con el anfitrión del alojamiento', () => {
    let creada: Conversacion | undefined;
    servicio.iniciar(9, '  Hola, ¿está disponible?  ').subscribe((c) => (creada = c));

    const req = http.expectOne('http://api/conversaciones');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ idAlojamiento: 9, mensaje: 'Hola, ¿está disponible?' });
    req.flush(conversacionApi, { status: 201, statusText: 'Created' });

    expect(creada?.id).toBe(5);
    expect(creada?.contacto).toBe('Luis Gómez');
  });
});
