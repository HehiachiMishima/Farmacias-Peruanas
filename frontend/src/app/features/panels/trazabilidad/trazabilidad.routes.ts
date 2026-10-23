import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PANEL_TRAZABILIDAD, primerModuloAccesible } from '../../../core/config/paneles';
import { panelGuard } from '../../../core/guards/permiso.guard';
import { SesionStore } from '../../../core/services/sesion';
import { PanelShell } from '../panel-shell';

function primerModuloTrazabilidad(): string {
  try {
    const modulo = primerModuloAccesible(PANEL_TRAZABILIDAD, inject(SesionStore).permisos());
    return modulo ? `/trazabilidad/${modulo.ruta}` : '/sin-acceso';
  } catch {
    return '/trazabilidad/resumen';
  }
}

export const TRAZABILIDAD_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_TRAZABILIDAD },
    children: [
      { path: '', pathMatch: 'full', redirectTo: primerModuloTrazabilidad },
      {
        path: 'resumen',
        loadComponent: () => import('./pages/resumen/resumen').then((m) => m.TrazabilidadResumen),
      },
    ],
  },
];
