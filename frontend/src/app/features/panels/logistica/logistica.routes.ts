import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PANEL_LOGISTICA, primerModuloAccesible } from '../../../core/config/paneles';
import { panelGuard } from '../../../core/guards/permiso.guard';
import { SesionStore } from '../../../core/services/sesion';
import { PanelShell } from '../panel-shell';

function primerModuloLogistica(): string {
  try {
    const modulo = primerModuloAccesible(PANEL_LOGISTICA, inject(SesionStore).permisos());
    return modulo ? `/logistica/${modulo.ruta}` : '/sin-acceso';
  } catch {
    return '/logistica/resumen';
  }
}

export const LOGISTICA_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_LOGISTICA },
    children: [
      { path: '', pathMatch: 'full', redirectTo: primerModuloLogistica },
      {
        path: 'resumen',
        loadComponent: () => import('./pages/resumen/resumen').then((m) => m.LogisticaResumen),
      },
    ],
  },
];
