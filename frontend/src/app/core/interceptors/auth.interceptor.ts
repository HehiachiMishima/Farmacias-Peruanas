import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SesionStore } from '../services/sesion';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const sesionStore = inject(SesionStore);
  const token = sesionStore.autenticado() ? sesionStore.sesion()?.token : null;

  if (token && !req.headers.has('Authorization')) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(req);
};
