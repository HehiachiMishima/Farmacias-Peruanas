import { Usuario } from './usuario.model';

export interface LoginRequest {
  usuario: string;
  contrasena: string;
}

export interface CambiarContrasenaRequest {
  contrasenaActual: string;
  contrasenaNueva: string;
}

export interface AuthResponse {
  token: string;
  tipo: string;
  expiraEn: string;
  usuario: Usuario;
  permisos: string[];
}

export interface Sesion {
  token: string;
  tipo: string;
  expiraEn: string;
  usuario: Usuario;
  permisos: string[];
}

/** Sesión vigente según el servidor (GET /auth/sesion). */
export interface SesionVigente {
  usuario: Usuario;
  permisos: string[];
}
