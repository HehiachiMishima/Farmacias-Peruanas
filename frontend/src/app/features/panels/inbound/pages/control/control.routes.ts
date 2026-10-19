import { Routes } from '@angular/router';
import { InboundControl } from './control';

export const INBOUND_CONTROL_ROUTES: Routes = [
  {
    path: '',
    component: InboundControl,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'proveedores' },
      {
        path: 'proveedores',
        loadComponent: () => import('./proveedores/proveedores').then((m) => m.ProveedoresInbound),
      },
      {
        path: 'tipos',
        loadComponent: () => import('./tipos/tipos').then((m) => m.TiposInbound),
      },
    ],
  },
];
