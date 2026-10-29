export interface UsuarioCrearRequest {
  usuario: string;
  contrasena: string;
  nombre: string;
  apellido: string;
  email: string;
  telefono?: string | null;
  activo?: boolean;
}

export interface UsuarioActualizarRequest {
  usuario?: string;
  contrasena?: string;
  nombre?: string;
  apellido?: string;
  email?: string;
  telefono?: string | null;
  activo?: boolean;
}

export interface UsuarioFiltro {
  usuario?: string;
  nombre?: string;
  apellido?: string;
  email?: string;
  activo?: boolean;
}

/** Roles y permisos efectivos de un usuario (GET /usuarios/resumen). */
export interface UsuarioResumen {
  usuarioId: number;
  roles: string[];
  permisos: string[];
}
