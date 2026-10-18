import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PANEL_BITACORA, primerModuloAccesible } from '../../../core/config/paneles';
import { PERMISOS } from '../../../core/config/permisos';
import { permisoGuard } from '../../../core/guards/permiso.guard';
import { SesionStore } from '../../../core/services/sesion';
import { PanelShell } from '../panel-shell';

function primerModuloBitacora(): string {
  try {
    const modulo = primerModuloAccesible(PANEL_BITACORA, inject(SesionStore).permisos());
    return modulo ? `/bitacora/${modulo.ruta}` : '/sin-acceso';
  } catch {
    return '/bitacora/incidencias';
  }
}

export const BITACORA_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_BITACORA },
    children: [
      { path: '', pathMatch: 'full', redirectTo: primerModuloBitacora },
      {
        path: 'incidencias',
        canActivate: [permisoGuard(PERMISOS.VER_INCIDENCIAS)],
        loadComponent: () =>
          import('./pages/incidencias/incidencias').then((m) => m.Incidencias),
      },
      {
        path: 'control',
        canActivate: [permisoGuard(PERMISOS.VER_CONTROL)],
        loadChildren: () =>
          import('./pages/control/control.routes').then((m) => m.CONTROL_ROUTES),
      },
      {
        path: 'dashboard',
        canActivate: [permisoGuard(PERMISOS.VER_DASHBOARD)],
        loadComponent: () =>
          import('./pages/dashboard/dashboard').then((m) => m.DashboardBitacora),
      },
    ],
  },
];
