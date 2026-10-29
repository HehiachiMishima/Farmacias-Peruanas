import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina } from '../../../../core/models';
import {
  Proveedor,
  ProveedorActualizarRequest,
  ProveedorCrearRequest,
  TipoIncidenciaInbound,
  TipoIncidenciaInboundActualizarRequest,
  TipoIncidenciaInboundCrearRequest,
} from './inbound.model';
import { Api } from '../../../../core/services/api';

@Service()
export class InboundControlApi {
  private readonly api = inject(Api);

  listarProveedores(): Observable<Proveedor[]> {
    return this.api.get<Proveedor[]>('/inbound/control/proveedores');
  }

  listarProveedoresPaginado(
    pagina = 1,
    tamano = 20,
    texto?: string,
  ): Observable<Pagina<Proveedor>> {
    return this.api.get<Pagina<Proveedor>>('/inbound/control/proveedores/paginado', {
      pagina,
      tamano,
      ...(texto?.trim() ? { texto: texto.trim() } : {}),
    });
  }

  crearProveedor(p: ProveedorCrearRequest): Observable<Proveedor> {
    return this.api.post<Proveedor>('/inbound/control/proveedores', p);
  }

  actualizarProveedor(id: number, p: ProveedorActualizarRequest): Observable<Proveedor> {
    return this.api.put<Proveedor>(`/inbound/control/proveedores/${id}`, p);
  }

  eliminarProveedor(id: number): Observable<void> {
    return this.api.delete<void>(`/inbound/control/proveedores/${id}`);
  }

  listarTipos(): Observable<TipoIncidenciaInbound[]> {
    return this.api.get<TipoIncidenciaInbound[]>('/inbound/control/incidencias');
  }

  listarTiposPaginado(
    pagina = 1,
    tamano = 20,
    texto?: string,
  ): Observable<Pagina<TipoIncidenciaInbound>> {
    return this.api.get<Pagina<TipoIncidenciaInbound>>('/inbound/control/incidencias/paginado', {
      pagina,
      tamano,
      ...(texto?.trim() ? { texto: texto.trim() } : {}),
    });
  }

  crearTipo(p: TipoIncidenciaInboundCrearRequest): Observable<TipoIncidenciaInbound> {
    return this.api.post<TipoIncidenciaInbound>('/inbound/control/incidencias', p);
  }

  actualizarTipo(
    id: number,
    p: TipoIncidenciaInboundActualizarRequest,
  ): Observable<TipoIncidenciaInbound> {
    return this.api.put<TipoIncidenciaInbound>(`/inbound/control/incidencias/${id}`, p);
  }

  eliminarTipo(id: number): Observable<void> {
    return this.api.delete<void>(`/inbound/control/incidencias/${id}`);
  }
}
