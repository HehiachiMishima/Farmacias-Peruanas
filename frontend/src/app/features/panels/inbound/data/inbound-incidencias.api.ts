import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnaliticaInbound } from './analitica.model';
import {
  IncidenciaInbound,
  IncidenciaInboundActualizarRequest,
  IncidenciaInboundCrearRequest,
  IncidenciaInboundFiltros,
} from './inbound.model';
import { Pagina } from '../../../../core/models';
import { Api } from '../../../../core/services/api';

@Service()
export class InboundIncidenciasApi {
  private readonly api = inject(Api);

  listar(
    filtros: IncidenciaInboundFiltros = {},
    pagina = 1,
    tamano = 10,
  ): Observable<Pagina<IncidenciaInbound>> {
    return this.api.get<Pagina<IncidenciaInbound>>('/inbound/incidencias', {
      ...filtros,
      pagina,
      tamano,
    } as Record<string, unknown>);
  }

  /** Analítica del dashboard en torno al proveedor. */
  analitica(filtros: IncidenciaInboundFiltros = {}): Observable<AnaliticaInbound> {
    return this.api.get<AnaliticaInbound>(
      '/inbound/incidencias/analitica',
      filtros as Record<string, unknown>,
    );
  }

  exportar(filtros: IncidenciaInboundFiltros = {}): Observable<IncidenciaInbound[]> {
    return this.api.get<IncidenciaInbound[]>(
      '/inbound/incidencias/exportar',
      filtros as Record<string, unknown>,
    );
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
