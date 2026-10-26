import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ArchivoSimulacion,
  BalanceoSimulacion,
  ConfiguracionSimulacion,
  ConfiguracionSimulacionRequest,
  DashboardSimulacion,
  HistorialSimulacion,
  Pagina,
  RegistroSimulacionActualizarRequest,
  RutaSimulacion,
  RutasSimulacionFiltros,
} from '../models';
import { Api } from './api';

@Service()
export class SimulacionApi {
  private readonly api = inject(Api);

  listar(
    pagina = 1,
    tamano = 10,
    texto = '',
    estados: string[] = [],
  ): Observable<Pagina<ArchivoSimulacion>> {
    return this.api.get<Pagina<ArchivoSimulacion>>('/simulacion/registros', {
      pagina,
      tamano,
      texto,
      ...(estados.length > 0 ? { estados: estados.join(',') } : {}),
    });
  }

  opciones(): Observable<ArchivoSimulacion[]> {
    return this.api.get<ArchivoSimulacion[]>('/simulacion/registros/opciones');
  }

  subir(formulario: FormData): Observable<ArchivoSimulacion> {
    return this.api.postForm<ArchivoSimulacion>('/simulacion/registros', formulario);
  }

  actualizar(
    id: number,
    peticion: RegistroSimulacionActualizarRequest,
  ): Observable<ArchivoSimulacion> {
    return this.api.put<ArchivoSimulacion>(`/simulacion/registros/${id}`, peticion);
  }

  eliminar(id: number): Observable<void> {
    return this.api.delete<void>(`/simulacion/registros/${id}`);
  }

  configuracion(id: number): Observable<ConfiguracionSimulacion> {
    return this.api.get<ConfiguracionSimulacion>(`/simulacion/registros/${id}/configuracion`);
  }

  /** Rutas paginadas del registro; el texto filtra por prefijo del código. */
  rutas(id: number, filtros: RutasSimulacionFiltros): Observable<Pagina<RutaSimulacion>> {
    return this.api.get<Pagina<RutaSimulacion>>(`/simulacion/registros/${id}/rutas`, {
      pagina: filtros.pagina,
      tamano: filtros.tamano,
      texto: filtros.texto,
      ...(filtros.clusterIds.length > 0 ? { clusterIds: filtros.clusterIds.join(',') } : {}),
      ...(filtros.chuteIds.length > 0 ? { chuteIds: filtros.chuteIds.join(',') } : {}),
    });
  }

  guardarConfiguracion(
    id: number,
    peticion: ConfiguracionSimulacionRequest,
  ): Observable<ConfiguracionSimulacion> {
    return this.api.put<ConfiguracionSimulacion>(
      `/simulacion/registros/${id}/configuracion`,
      peticion,
    );
  }

  moverRuta(id: number, rutaId: number, chuteId: number): Observable<ConfiguracionSimulacion> {
    return this.api.put<ConfiguracionSimulacion>(
      `/simulacion/registros/${id}/rutas/${rutaId}/mover`,
      { chuteId },
    );
  }

  balancear(id: number): Observable<BalanceoSimulacion> {
    return this.api.post<BalanceoSimulacion>(`/simulacion/registros/${id}/balancear`);
  }

  restablecer(id: number): Observable<void> {
    return this.api.post<void>(`/simulacion/registros/${id}/restablecer`);
  }

  historial(id: number): Observable<HistorialSimulacion[]> {
    return this.api.get<HistorialSimulacion[]>(`/simulacion/registros/${id}/historial`);
  }

  dashboard(id: number): Observable<DashboardSimulacion> {
    return this.api.get<DashboardSimulacion>(`/simulacion/registros/${id}/dashboard`);
  }
}
