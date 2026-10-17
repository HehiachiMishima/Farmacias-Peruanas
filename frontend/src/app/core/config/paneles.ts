import { PERMISOS } from './permisos';

export interface ModuloPanel {
  id: string;
  nombre: string;
  descripcion: string;
  icono: string;
  ruta: string;
  permiso: string;
}

export interface PanelDef {
  id: string;
  nombre: string;
  descripcion: string;
  icono: string;
  ruta: string;
  permiso: string;
  acento: string;
  modulos: ModuloPanel[];
}

export const PANEL_ADMIN: PanelDef = {
  id: 'admin',
  nombre: 'Administración',
  descripcion: 'Gobierno de identidades, accesos y trazabilidad del sistema.',
  icono: 'shield',
  ruta: '/admin',
  permiso: PERMISOS.PANEL_ADMIN,
  acento: 'brand',
  modulos: [
    {
      id: 'dashboard',
      nombre: 'Dashboard',
      descripcion: 'Resumen operativo del control de accesos.',
      icono: 'dashboard',
      ruta: 'dashboard',
      permiso: PERMISOS.DASHBOARD_ADMIN,
    },
    {
      id: 'usuarios',
      nombre: 'Usuarios',
      descripcion: 'Altas, edición, estado y asignación de roles.',
      icono: 'usuarios',
      ruta: 'usuarios',
      permiso: PERMISOS.VER_USUARIOS,
    },
    {
      id: 'roles',
      nombre: 'Roles',
      descripcion: 'Roles y los permisos que conceden.',
      icono: 'roles',
      ruta: 'roles',
      permiso: PERMISOS.VER_ROLES,
    },
    {
      id: 'permisos',
      nombre: 'Permisos',
      descripcion: 'Catálogo de permisos del sistema.',
      icono: 'permisos',
      ruta: 'permisos',
      permiso: PERMISOS.VER_PERMISOS,
    },
  ],
}

export const PANELES: PanelDef[] = [PANEL_ADMIN];

export function primerModuloAccesible(panel: PanelDef, permisos: readonly string[]): ModuloPanel | undefined {
  return panel.modulos.find((modulo) => permisos.includes(modulo.permiso));
}

export function tienePermiso(permisos: readonly string[], permiso: string): boolean {
  return permisos.includes(permiso);
}
