import { Component } from '@angular/core';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';

@Component({
  selector: 'app-bitacora-incidencias',
  imports: [EmptyState],
  template: `
    <header class="page-head">
      <div>
        <h1 class="page-head__title">Incidencias</h1>
        <p class="page-head__sub">Registro y seguimiento de incidencias.</p>
      </div>
    </header>

    <div class="table-card">
      <ui-empty
        icono="incidencias"
        titulo="Módulo en construcción"
        mensaje="El registro de incidencias estará disponible próximamente."
      />
    </div>
  `,
})
export class Incidencias {}
