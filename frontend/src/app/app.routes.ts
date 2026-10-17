import { Routes } from '@angular/router';
import { PERMISOS } from './core/config/permisos';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { permisoGuard } from './core/guards/permiso.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
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
  { path: '**', redirectTo: '' },
];
