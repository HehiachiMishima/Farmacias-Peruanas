import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SesionStore } from '../services/sesion';

export function permisoGuard(permiso: string): CanActivateFn {
  return () => {
    const sesion = inject(SesionStore);
    const router = inject(Router);

    if (!sesion.autenticado()) {
      return router.createUrlTree(['/login']);
    }
    if (sesion.tienePermiso(permiso)) {
      return true;
    }
    return router.createUrlTree(['/sin-acceso'], { queryParams: { permiso } });
  };
}
