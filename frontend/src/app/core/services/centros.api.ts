import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Centro,
  CentroActualizarRequest,
  CentroCrearRequest,
} from '../models';
import { Api } from './api';

@Service()
export class CentrosApi {
  private readonly api = inject(Api);

  listar(): Observable<Centro[]> {
    return this.api.get<Centro[]>('/centros');
  }

  crear(peticion: CentroCrearRequest): Observable<Centro> {
    return this.api.post<Centro>('/centros', peticion);
  }

  actualizar(id: number, peticion: CentroActualizarRequest): Observable<Centro> {
    return this.api.put<Centro>(`/centros/${id}`, peticion);
  }

  eliminar(id: number): Observable<void> {
    return this.api.delete<void>(`/centros/${id}`);
  }
}
