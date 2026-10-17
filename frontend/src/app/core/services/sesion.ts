import { Service, computed, signal } from '@angular/core';
import { CONFIG } from '../config/configuracion';
import { Sesion } from '../models';

const CLAVE = CONFIG.almacenamiento.sesion;

@Service()
export class SesionStore {
  private readonly _sesion = signal<Sesion | null>(this.leer());
  private readonly _permisos = computed<readonly string[]>(() => this._sesion()?.permisos ?? []);

  readonly sesion = this._sesion.asReadonly();
  readonly permisos = this._permisos;
  readonly usuario = computed(() => this._sesion()?.usuario ?? null);
  readonly autenticado = computed(() => this.esVigente(this._sesion()));

  guardar(sesion: Sesion): void {
    this._sesion.set(sesion);
    try {
      localStorage.setItem(CLAVE, JSON.stringify(sesion));
    } catch {
      /* almacenamiento no disponible */
    }
  }

  limpiar(): void {
    this._sesion.set(null);
    try {
      localStorage.removeItem(CLAVE);
    } catch {
      /* almacenamiento no disponible */
    }
  }

  tienePermiso(permiso: string): boolean {
    return this._permisos().includes(permiso);
  }

  tieneAlguno(permisos: readonly string[]): boolean {
    const actuales = this._permisos();
    return permisos.some((permiso) => actuales.includes(permiso));
  }

  tieneTodos(permisos: readonly string[]): boolean {
    const actuales = this._permisos();
    return permisos.every((permiso) => actuales.includes(permiso));
  }

  private leer(): Sesion | null {
    try {
      const crudo = localStorage.getItem(CLAVE);
      if (!crudo) {
        return null;
      }
      const sesion = JSON.parse(crudo) as Sesion;
      if (this.esVigente(sesion)) {
        return sesion;
      }
      localStorage.removeItem(CLAVE);
      return null;
    } catch {
      return null;
    }
  }

  private esVigente(sesion: Sesion | null): boolean {
    if (!sesion?.token) {
      return false;
    }
    if (sesion.expiraEn) {
      return new Date(sesion.expiraEn).getTime() > Date.now();
    }
    return true;
  }
}
