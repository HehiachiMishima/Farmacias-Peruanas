import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PANEL_INBOUND, primerModuloAccesible } from '../../../core/config/paneles';
import { PERMISOS } from '../../../core/config/permisos';
import { permisoGuard } from '../../../core/guards/permiso.guard';
import { SesionStore } from '../../../core/services/sesion';
import { PanelShell } from '../panel-shell';

function primerModuloInbound(): string {
  try {
    const modulo = primerModuloAccesible(PANEL_INBOUND, inject(SesionStore).permisos());
    return modulo ? `/inbound/${modulo.ruta}` : '/sin-acceso';
  } catch {
    return '/inbound/incidencias';
  }
}

export const INBOUND_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_INBOUND },
    children: [
      { path: '', pathMatch: 'full', redirectTo: primerModuloInbound },
      {
        path: 'incidencias',
        canActivate: [permisoGuard(PERMISOS.VER_INCIDENCIAS_INBOUND)],
        loadComponent: () =>
          import('./pages/incidencias/incidencias').then((m) => m.InboundIncidencias),
      },
      {
        path: 'control',
        canActivate: [permisoGuard(PERMISOS.VER_CONTROL_INBOUND)],
        loadChildren: () =>
          import('./pages/control/control.routes').then((m) => m.INBOUND_CONTROL_ROUTES),
      },
      {
        path: 'dashboard',
        canActivate: [permisoGuard(PERMISOS.VER_DASHBOARD_INBOUND)],
        loadComponent: () =>
          import('./pages/dashboard/dashboard').then((m) => m.InboundDashboard),
      },
    ],
  },
];
