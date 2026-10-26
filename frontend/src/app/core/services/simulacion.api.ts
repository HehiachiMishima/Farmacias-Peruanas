import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ArchivoSimulacion,
  BalanceoSimulacion,
  ConfiguracionSimulacion,
  ConfiguracionSimulacionRequest,
  DashboardSimulacion,
  HistorialSimulacion,
  PaginaSimulacion,
  RegistroSimulacionActualizarRequest,
  ResumenSimulacion,
} from '../models';
import { Api } from './api';

@Service()
export class SimulacionApi {
  private readonly api = inject(Api);

  listar(pagina = 1, tamano = 10, texto = ''): Observable<PaginaSimulacion<ArchivoSimulacion>> {
    return this.api.get<PaginaSimulacion<ArchivoSimulacion>>('/simulacion/registros', {
      pagina,
      tamano,
      texto,
    });
  }

  resumen(): Observable<ResumenSimulacion> {
    return this.api.get<ResumenSimulacion>('/simulacion/registros/resumen');
  }

  opciones(): Observable<ArchivoSimulacion[]> {
    return this.api.get<ArchivoSimulacion[]>('/simulacion/registros/opciones');
  }

  obtener(id: number): Observable<ArchivoSimulacion> {
    return this.api.get<ArchivoSimulacion>(`/simulacion/registros/${id}`);
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
