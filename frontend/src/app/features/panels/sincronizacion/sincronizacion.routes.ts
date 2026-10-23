import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PANEL_SINCRONIZACION, primerModuloAccesible } from '../../../core/config/paneles';
import { panelGuard } from '../../../core/guards/permiso.guard';
import { SesionStore } from '../../../core/services/sesion';
import { PanelShell } from '../panel-shell';

function primerModuloSincronizacion(): string {
  try {
    const modulo = primerModuloAccesible(PANEL_SINCRONIZACION, inject(SesionStore).permisos());
    return modulo ? `/sincronizacion/${modulo.ruta}` : '/sin-acceso';
  } catch {
    return '/sincronizacion/resumen';
  }
}

export const SINCRONIZACION_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_SINCRONIZACION },
    children: [
      { path: '', pathMatch: 'full', redirectTo: primerModuloSincronizacion },
      {
        path: 'resumen',
        loadComponent: () =>
          import('./pages/resumen/resumen').then((m) => m.SincronizacionResumen),
      },
    ],
  },
];
