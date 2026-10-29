import { Routes } from '@angular/router';
import { CATALOGO_PROVEEDORES, CATALOGO_TIPOS } from './catalogo/catalogos';
import { InboundControl } from './control';

export const INBOUND_CONTROL_ROUTES: Routes = [
  {
    path: '',
    component: InboundControl,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'proveedores' },
      {
        path: 'proveedores',
        data: { catalogo: CATALOGO_PROVEEDORES },
        loadComponent: () => import('./catalogo/catalogo').then((m) => m.CatalogoInbound),
      },
      {
        path: 'tipos',
        data: { catalogo: CATALOGO_TIPOS },
        loadComponent: () => import('./catalogo/catalogo').then((m) => m.CatalogoInbound),
      },
    ],
  },
];
