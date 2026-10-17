import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Permiso,
  PermisoActualizarRequest,
  PermisoCrearRequest,
  PermisoFiltro,
  Rol,
} from '../models';
import { Api } from './api';

@Service()
export class PermisosApi {
  private readonly api = inject(Api);

  listar(): Observable<Permiso[]> {
    return this.api.get<Permiso[]>('/permisos');
  }

  buscar(filtro: PermisoFiltro): Observable<Permiso[]> {
    return this.api.get<Permiso[]>('/permisos/buscar', { ...filtro });
  }

  obtener(id: number): Observable<Permiso> {
    return this.api.get<Permiso>(`/permisos/${id}`);
  }

  crear(peticion: PermisoCrearRequest): Observable<Permiso> {
    return this.api.post<Permiso>('/permisos', peticion);
  }

  actualizar(id: number, peticion: PermisoActualizarRequest): Observable<Permiso> {
    return this.api.put<Permiso>(`/permisos/${id}`, peticion);
  }

  eliminar(id: number): Observable<void> {
    return this.api.delete<void>(`/permisos/${id}`);
  }

  cambiarActivo(id: number, activo: boolean): Observable<Permiso> {
    return this.api.patch<Permiso>(`/permisos/${id}/activo`, { activo });
  }

  roles(id: number): Observable<Rol[]> {
    return this.api.get<Rol[]>(`/permisos/${id}/roles`);
  }
}
