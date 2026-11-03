import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Api } from '../../../../core/services/api';
import { CentroMalla, CentroMallaDatos, CentroMallaResumen, EvaluacionMalla } from './malla.model';

@Service()
export class MallaApi {
  private readonly api = inject(Api);

  centros(): Observable<CentroMallaResumen[]> {
    return this.api.get<CentroMallaResumen[]>('/malla/centros');
  }

  centro(id: number): Observable<CentroMalla> {
    return this.api.get<CentroMalla>(`/malla/centros/${id}`);
  }

  crear(datos: CentroMallaDatos): Observable<CentroMalla> {
    return this.api.post<CentroMalla>('/malla/centros', datos);
  }

  guardar(id: number, datos: CentroMallaDatos): Observable<CentroMalla> {
    return this.api.put<CentroMalla>(`/malla/centros/${id}`, datos);
  }

  eliminar(id: number): Observable<void> {
    return this.api.delete<void>(`/malla/centros/${id}`);
  }

  /** Lee un Excel con la plantilla y devuelve sus datos sin guardarlos. */
  importar(archivo: File): Observable<CentroMallaDatos> {
    const formulario = new FormData();
    formulario.append('archivo', archivo);
    return this.api.postForm<CentroMallaDatos>('/malla/centros/importar', formulario);
  }

  plantilla(): Observable<Blob> {
    return this.api.getArchivo('/malla/centros/plantilla');
  }

  exportar(id: number): Observable<Blob> {
    return this.api.getArchivo(`/malla/centros/${id}/excel`);
  }

  /** Reglas del servidor evaluadas sobre la malla de todos los centros. */
  evaluacion(): Observable<EvaluacionMalla> {
    return this.api.get<EvaluacionMalla>('/malla/configuracion/evaluacion');
  }

  /** Activa o apaga una regla o ajusta sus parámetros; luego se vuelve a evaluar. */
  actualizarRegla(
    id: number,
    cambios: { activa?: boolean; parametros?: Record<string, string> },
  ): Observable<unknown> {
    return this.api.put<unknown>(`/malla/configuracion/reglas/${id}`, cambios);
  }
}
