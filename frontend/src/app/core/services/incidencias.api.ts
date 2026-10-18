import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Incidencia,
  IncidenciaActualizarRequest,
  IncidenciaCrearRequest,
} from '../models';
import { Api } from './api';

@Service()
export class IncidenciasApi {
  private readonly api = inject(Api);

  listar(): Observable<Incidencia[]> {
    return this.api.get<Incidencia[]>('/incidencias');
  }

  crear(peticion: IncidenciaCrearRequest): Observable<Incidencia> {
    return this.api.post<Incidencia>('/incidencias', peticion);
  }

  actualizar(id: number, peticion: IncidenciaActualizarRequest): Observable<Incidencia> {
    return this.api.put<Incidencia>(`/incidencias/${id}`, peticion);
  }

  eliminar(id: number): Observable<void> {
    return this.api.delete<void>(`/incidencias/${id}`);
  }
}
