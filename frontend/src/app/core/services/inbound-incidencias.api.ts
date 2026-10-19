import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  EstadisticaInbound,
  IncidenciaInbound,
  IncidenciaInboundActualizarRequest,
  IncidenciaInboundCrearRequest,
  IncidenciaInboundFiltros,
  PaginaInbound,
} from '../models';
import { Api } from './api';

@Service()
export class InboundIncidenciasApi {
  private readonly api = inject(Api);

  listar(filtros: IncidenciaInboundFiltros = {}, pagina = 1, tamano = 10): Observable<PaginaInbound<IncidenciaInbound>> {
    return this.api.get<PaginaInbound<IncidenciaInbound>>('/inbound/incidencias', { ...filtros, pagina, tamano } as Record<string, unknown>);
  }

  estadisticas(filtros: IncidenciaInboundFiltros = {}): Observable<EstadisticaInbound> {
    return this.api.get<EstadisticaInbound>('/inbound/incidencias/estadisticas', filtros as Record<string, unknown>);
  }

  exportar(filtros: IncidenciaInboundFiltros = {}): Observable<IncidenciaInbound[]> {
    return this.api.get<IncidenciaInbound[]>('/inbound/incidencias/exportar', filtros as Record<string, unknown>);
  }

  crear(p: IncidenciaInboundCrearRequest): Observable<IncidenciaInbound> {
    return this.api.post<IncidenciaInbound>('/inbound/incidencias', p);
  }

  actualizar(id: number, p: IncidenciaInboundActualizarRequest): Observable<IncidenciaInbound> {
    return this.api.put<IncidenciaInbound>(`/inbound/incidencias/${id}`, p);
  }

  eliminar(id: number): Observable<void> {
    return this.api.delete<void>(`/inbound/incidencias/${id}`);
  }
}
