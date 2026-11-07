import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_URL } from './api-url';
import { SessionService } from './session.service';
/**
 * Agrega "Authorization: Bearer <jwt>" a las peticiones hacia nuestro backend.
 * Si no hay sesión vigente, la petición sale sin token (el backend responderá 401).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(SessionService).obtenerToken();
  // Solo enviamos el token a nuestra API, nunca a dominios externos
  if (!token || !req.url.startsWith(inject(API_URL))) {
    return next(req);
  }
  return next(
    req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
