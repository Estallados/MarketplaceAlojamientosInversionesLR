import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { Mensajes } from './mensajes';
import { MensajeriaService } from '../services/mensajeria.service';
import { SessionService } from '../../core/services/session.service';
import { Conversacion, Mensaje } from '../models/mensajeria';

/**
 * HU-28 Mensajería entre huésped y anfitrión (SCRUM-278).
 */
describe('Mensajes (HU-28)', () => {
  let servicio: {
    conversaciones: ReturnType<typeof vi.fn>;
    mensajes: ReturnType<typeof vi.fn>;
    enviar: ReturnType<typeof vi.fn>;
    mensajeDeError: ReturnType<typeof vi.fn>;
  };

  const casa: Conversacion = {
    id: 5,
    contacto: 'Luis Gómez',
    iniciales: 'LG',
    miId: 7,
    idAlojamiento: 9,
    alojamiento: 'Casa en la playa',
    fechaCreacion: '2026-11-10T10:00:00',
  };
  const loft: Conversacion = { ...casa, id: 6, contacto: 'Marta Ruiz', iniciales: 'MR', idAlojamiento: 3, alojamiento: 'Loft' };
  const pregunta: Mensaje = { id: 1, texto: '¿Hay parqueadero?', fecha: '2026-11-10T10:00:00', remitente: 'Ana', mio: true };

  function crear(query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [Mensajes],
      providers: [
        provideRouter([]),
        { provide: MensajeriaService, useValue: servicio },
        { provide: SessionService, useValue: { rol: () => 'Huésped' } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
      ],
    });
    const c = TestBed.createComponent(Mensajes).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    servicio = {
      conversaciones: vi.fn().mockReturnValue(of([casa, loft])),
      mensajes: vi.fn().mockReturnValue(of([pregunta])),
      enviar: vi.fn(),
      mensajeDeError: vi.fn((e: HttpErrorResponse, defecto: string) => e.error?.mensaje ?? defecto),
    };
  });

  it('CA-165: envía un mensaje y lo agrega a la conversación como propio', () => {
    const c = crear();
    expect(c.selectedId()).toBe(5);
    expect(c.messages()).toEqual([pregunta]);

    const respuesta: Mensaje = { id: 2, texto: 'Gracias', fecha: '2026-11-10T10:06:00', remitente: 'Ana', mio: true };
    servicio.enviar.mockReturnValue(of(respuesta));
    c.draft.set('  Gracias  ');
    c.send();

    expect(servicio.enviar).toHaveBeenCalledWith(casa, 'Gracias');
    expect(c.messages().map((m) => m.texto)).toEqual(['¿Hay parqueadero?', 'Gracias']);
    expect(c.draft()).toBe('');
  });

  it('CA-165: no envía mensajes vacíos y conserva el borrador si falla', () => {
    const c = crear();
    c.draft.set('   ');
    c.send();
    expect(servicio.enviar).not.toHaveBeenCalled();

    servicio.enviar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    c.draft.set('Hola');
    c.send();
    expect(c.errorEnvio()).toBe('No se pudo enviar el mensaje.');
    expect(c.draft()).toBe('Hola');
  });

  it('CA-167: si no participa en la conversación muestra el error y no los mensajes', () => {
    servicio.mensajes.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { mensaje: 'No participas en esta conversación.' } })),
    );
    const c = crear();

    expect(c.messages()).toEqual([]);
    expect(c.errorMensajes()).toBe('No participas en esta conversación.');
  });

  it('CA-169: abre la conversación recién iniciada desde el alojamiento', () => {
    const c = crear({ conversacion: '6' });

    expect(c.selectedId()).toBe(6);
    expect(c.active()?.contacto).toBe('Marta Ruiz');
    expect(servicio.mensajes).toHaveBeenCalledWith(loft);
  });

  it('filtra las conversaciones por contacto o alojamiento', () => {
    const c = crear();
    c.search.set('loft');
    expect(c.filtered()).toEqual([loft]);
  });
});
