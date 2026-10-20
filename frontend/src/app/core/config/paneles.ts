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
  /** Color de señal del panel (CSS): tarjeta del launcher y detalles del panel. */
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
  acento: 'var(--violet)',
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

export const PANEL_BITACORA: PanelDef = {
  id: 'bitacora',
  nombre: 'Bitácora',
  descripcion: 'Registro de incidencias, catálogos de control y métricas operativas.',
  icono: 'bitacora',
  ruta: '/bitacora',
  permiso: PERMISOS.PANEL_BITACORA,
  acento: 'var(--brand)',
  modulos: [
    {
      id: 'incidencias',
      nombre: 'Incidencias',
      descripcion: 'Registro y seguimiento de incidencias.',
      icono: 'incidencias',
      ruta: 'incidencias',
      permiso: PERMISOS.VER_INCIDENCIAS,
    },
    {
      id: 'control',
      nombre: 'Control',
      descripcion: 'Centros, sistemas, áreas y responsables.',
      icono: 'control',
      ruta: 'control',
      permiso: PERMISOS.VER_CONTROL,
    },
    {
      id: 'dashboard',
      nombre: 'Dashboard',
      descripcion: 'Indicadores de la bitácora.',
      icono: 'dashboard',
      ruta: 'dashboard',
      permiso: PERMISOS.VER_DASHBOARD,
    },
  ],
}

export const PANEL_INBOUND: PanelDef = {
  id: 'inbound',
  nombre: 'Inbound',
  descripcion: 'Incidencias de inbound, catálogos de control y métricas operativas.',
  icono: 'truck',
  ruta: '/inbound',
  permiso: PERMISOS.PANEL_INBOUND,
  acento: 'var(--accent)',
  modulos: [
    {
      id: 'incidencias',
      nombre: 'Incidencias',
      descripcion: 'Registro y seguimiento de incidencias de inbound.',
      icono: 'incidencias',
      ruta: 'incidencias',
      permiso: PERMISOS.VER_INCIDENCIAS_INBOUND,
    },
    {
      id: 'control',
      nombre: 'Control',
      descripcion: 'Proveedores y tipos de incidencia.',
      icono: 'control',
      ruta: 'control',
      permiso: PERMISOS.VER_CONTROL_INBOUND,
    },
    {
      id: 'dashboard',
      nombre: 'Dashboard',
      descripcion: 'Indicadores de inbound.',
      icono: 'dashboard',
      ruta: 'dashboard',
      permiso: PERMISOS.VER_DASHBOARD_INBOUND,
    },
  ],
}

export const PANELES: PanelDef[] = [PANEL_ADMIN, PANEL_BITACORA, PANEL_INBOUND];

export function primerModuloAccesible(panel: PanelDef, permisos: readonly string[]): ModuloPanel | undefined {
  return panel.modulos.find((modulo) => permisos.includes(modulo.permiso));
}

export function tienePermiso(permisos: readonly string[], permiso: string): boolean {
  return permisos.includes(permiso);
}
