import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnaliticaAdmin } from '../models';
import { Api } from './api';

@Service()
export class AdminAnaliticaApi {
  private readonly api = inject(Api);

  /** Indicadores ejecutivos del gobierno de accesos. */
  analitica(): Observable<AnaliticaAdmin> {
    return this.api.get<AnaliticaAdmin>('/admin/analitica');
  }
}
