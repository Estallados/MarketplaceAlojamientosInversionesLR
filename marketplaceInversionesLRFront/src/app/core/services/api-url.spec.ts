import { API_URL_LOCAL, leerApiUrl } from './api-url';

describe('leerApiUrl', () => {
  it('usa la URL de config.js sin la barra final', () => {
    expect(leerApiUrl({ __config: { apiUrl: 'https://back.onrender.com/' } })).toBe('https://back.onrender.com');
  });

  it('usa localhost si config.js no existe o viene vacío', () => {
    expect(leerApiUrl({})).toBe(API_URL_LOCAL);
    expect(leerApiUrl({ __config: { apiUrl: '  ' } })).toBe(API_URL_LOCAL);
  });
});
