import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PANEL_ADMIN, primerModuloAccesible } from '../../../core/config/paneles';
import { PERMISOS } from '../../../core/config/permisos';
import { permisoGuard } from '../../../core/guards/permiso.guard';
import { SesionStore } from '../../../core/services/sesion';
import { PanelShell } from '../panel-shell';

function primerModuloAdmin(): string {
  try {
    const modulo = primerModuloAccesible(PANEL_ADMIN, inject(SesionStore).permisos());
    return modulo ? `/admin/${modulo.ruta}` : '/sin-acceso';
  } catch {
    return '/admin/dashboard';
  }
}

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: PanelShell,
    data: { panel: PANEL_ADMIN },
    children: [
      { path: '', pathMatch: 'full', redirectTo: primerModuloAdmin },
      {
        path: 'dashboard',
        canActivate: [permisoGuard(PERMISOS.DASHBOARD_ADMIN)],
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'usuarios',
        canActivate: [permisoGuard(PERMISOS.VER_USUARIOS)],
        loadComponent: () => import('./pages/usuarios/usuarios').then((m) => m.Usuarios),
      },
      {
        path: 'roles',
        canActivate: [permisoGuard(PERMISOS.VER_ROLES)],
        loadComponent: () => import('./pages/roles/roles').then((m) => m.Roles),
      },
      {
        path: 'permisos',
        canActivate: [permisoGuard(PERMISOS.VER_PERMISOS)],
        loadComponent: () => import('./pages/permisos/permisos').then((m) => m.Permisos),
      },
    ],
  },
];
