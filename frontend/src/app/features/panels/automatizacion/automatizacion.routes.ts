import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PANEL_AUTOMATIZACION, primerModuloAccesible } from '../../../core/config/paneles';
import { panelGuard } from '../../../core/guards/permiso.guard';
import { SesionStore } from '../../../core/services/sesion';
import { PanelShell } from '../panel-shell';

function primerModuloAutomatizacion(): string {
  try {
    const modulo = primerModuloAccesible(PANEL_AUTOMATIZACION, inject(SesionStore).permisos());
    return modulo ? `/automatizacion/${modulo.ruta}` : '/sin-acceso';
  } catch {
    return '/automatizacion/resumen';
  }
}

export const AUTOMATIZACION_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_AUTOMATIZACION },
    children: [
      { path: '', pathMatch: 'full', redirectTo: primerModuloAutomatizacion },
      {
        path: 'resumen',
        loadComponent: () =>
          import('./pages/resumen/resumen').then((m) => m.AutomatizacionResumen),
      },
    ],
  },
];
