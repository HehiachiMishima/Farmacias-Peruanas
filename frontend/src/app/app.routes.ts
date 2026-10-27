import { Routes } from '@angular/router';
import {
  PANEL_ANALISIS,
  PANEL_AUTOMATIZACION,
  PANEL_LOGISTICA,
  PANEL_SINCRONIZACION,
  PANEL_TRAZABILIDAD,
} from './core/config/paneles';
import { PERMISOS } from './core/config/permisos';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { introGuard } from './core/guards/intro.guard';
import { panelGuard, permisoGuard } from './core/guards/permiso.guard';

export const routes: Routes = [
  {
    path: 'intro',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/intro/intro').then((m) => m.Intro),
  },
  {
    path: 'login',
    canActivate: [guestGuard, introGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/launcher/launcher').then((m) => m.Launcher),
  },
  {
    path: 'sin-acceso',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sin-acceso/sin-acceso').then((m) => m.SinAcceso),
  },
  {
    path: 'admin',
    canActivate: [permisoGuard(PERMISOS.PANEL_ADMIN)],
    loadChildren: () => import('./features/panels/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: 'bitacora',
    canActivate: [permisoGuard(PERMISOS.PANEL_BITACORA)],
    loadChildren: () =>
      import('./features/panels/bitacora/bitacora.routes').then((m) => m.BITACORA_ROUTES),
  },
  {
    path: 'inbound',
    canActivate: [permisoGuard(PERMISOS.PANEL_INBOUND)],
    loadChildren: () =>
      import('./features/panels/inbound/inbound.routes').then((m) => m.INBOUND_ROUTES),
  },
  {
    path: 'simulacion',
    canActivate: [permisoGuard(PERMISOS.PANEL_SIMULACION)],
    loadChildren: () =>
      import('./features/panels/simulacion/simulacion.routes').then(
        (m) => m.SIMULACION_ROUTES,
      ),
  },
  {
    path: 'analisis',
    canActivate: [panelGuard(PANEL_ANALISIS)],
    loadChildren: () =>
      import('./features/panels/analisis/analisis.routes').then((m) => m.ANALISIS_ROUTES),
  },
  {
    path: 'trazabilidad',
    canActivate: [panelGuard(PANEL_TRAZABILIDAD)],
    loadChildren: () =>
      import('./features/panels/trazabilidad/trazabilidad.routes').then(
        (m) => m.TRAZABILIDAD_ROUTES,
      ),
  },
  {
    path: 'logistica',
    canActivate: [panelGuard(PANEL_LOGISTICA)],
    loadChildren: () =>
      import('./features/panels/logistica/logistica.routes').then((m) => m.LOGISTICA_ROUTES),
  },
  {
    path: 'automatizacion',
    canActivate: [panelGuard(PANEL_AUTOMATIZACION)],
    loadChildren: () =>
      import('./features/panels/automatizacion/automatizacion.routes').then(
        (m) => m.AUTOMATIZACION_ROUTES,
      ),
  },
  {
    path: 'sincronizacion',
    canActivate: [panelGuard(PANEL_SINCRONIZACION)],
    loadChildren: () =>
      import('./features/panels/sincronizacion/sincronizacion.routes').then(
        (m) => m.SINCRONIZACION_ROUTES,
      ),
  },
  { path: '**', redirectTo: '' },
];
