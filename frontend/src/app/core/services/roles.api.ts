import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Permiso,
  Rol,
  RolActualizarRequest,
  RolCrearRequest,
  RolFiltro,
  Usuario,
} from '../models';
import { Api } from './api';

@Service()
export class RolesApi {
  private readonly api = inject(Api);

  listar(): Observable<Rol[]> {
    return this.api.get<Rol[]>('/roles');
  }

  buscar(filtro: RolFiltro): Observable<Rol[]> {
    return this.api.get<Rol[]>('/roles/buscar', { ...filtro });
  }

  obtener(id: number): Observable<Rol> {
    return this.api.get<Rol>(`/roles/${id}`);
  }

  crear(peticion: RolCrearRequest): Observable<Rol> {
    return this.api.post<Rol>('/roles', peticion);
  }

  actualizar(id: number, peticion: RolActualizarRequest): Observable<Rol> {
    return this.api.put<Rol>(`/roles/${id}`, peticion);
  }

  eliminar(id: number): Observable<void> {
    return this.api.delete<void>(`/roles/${id}`);
  }

  cambiarActivo(id: number, activo: boolean): Observable<Rol> {
    return this.api.patch<Rol>(`/roles/${id}/activo`, { activo });
  }

  permisos(id: number): Observable<Permiso[]> {
    return this.api.get<Permiso[]>(`/roles/${id}/permisos`);
  }

  asignarPermiso(id: number, permisoId: number): Observable<unknown> {
    return this.api.post(`/roles/${id}/permisos`, { permisoId });
  }

  quitarPermiso(id: number, permisoId: number): Observable<void> {
    return this.api.delete<void>(`/roles/${id}/permisos/${permisoId}`);
  }

  usuarios(id: number): Observable<Usuario[]> {
    return this.api.get<Usuario[]>(`/roles/${id}/usuarios`);
  }
}
