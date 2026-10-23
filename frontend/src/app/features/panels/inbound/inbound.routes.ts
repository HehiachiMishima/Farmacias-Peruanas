import { Routes } from '@angular/router';
import { PANEL_INBOUND } from '../../../core/config/paneles';
import { PERMISOS } from '../../../core/config/permisos';
import { permisoGuard } from '../../../core/guards/permiso.guard';
import { PanelShell } from '../panel-shell';
import { aPrimerModulo } from '../panel-rutas';

export const INBOUND_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_INBOUND },
    children: [
      { path: '', pathMatch: 'full', redirectTo: aPrimerModulo(PANEL_INBOUND) },
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
