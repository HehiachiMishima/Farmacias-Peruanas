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

/**
 * Guard de acceso a un panel. A diferencia de permisoGuard, permite el paso a
 * los paneles marcados como `publico` (aún sin permisos), exigiendo solo estar
 * autenticado.
 */
export function panelGuard(panel: { permiso: string; publico?: boolean }): CanActivateFn {
  return () => {
    const sesion = inject(SesionStore);
    const router = inject(Router);

    if (!sesion.autenticado()) {
      return router.createUrlTree(['/login']);
    }
    if (panel.publico === true || sesion.tienePermiso(panel.permiso)) {
      return true;
    }
    return router.createUrlTree(['/sin-acceso'], { queryParams: { permiso: panel.permiso } });
  };
}
