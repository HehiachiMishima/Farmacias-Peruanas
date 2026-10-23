import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PANEL_ANALISIS, primerModuloAccesible } from '../../../core/config/paneles';
import { panelGuard } from '../../../core/guards/permiso.guard';
import { SesionStore } from '../../../core/services/sesion';
import { PanelShell } from '../panel-shell';

function primerModuloAnalisis(): string {
  try {
    const modulo = primerModuloAccesible(PANEL_ANALISIS, inject(SesionStore).permisos());
    return modulo ? `/analisis/${modulo.ruta}` : '/sin-acceso';
  } catch {
    return '/analisis/resumen';
  }
}

export const ANALISIS_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_ANALISIS },
    children: [
      { path: '', pathMatch: 'full', redirectTo: primerModuloAnalisis },
      {
        path: 'resumen',
        loadComponent: () => import('./pages/resumen/resumen').then((m) => m.AnalisisResumen),
      },
    ],
  },
];
