import { Component } from '@angular/core';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';

@Component({
  selector: 'app-bitacora-dashboard',
  imports: [EmptyState],
  template: `
    <header class="page-head">
      <div>
        <h1 class="page-head__title">Dashboard</h1>
        <p class="page-head__sub">Indicadores de la bitácora.</p>
      </div>
    </header>

    <div class="table-card">
      <ui-empty
        icono="dashboard"
        titulo="Módulo en construcción"
        mensaje="El dashboard de indicadores estará disponible próximamente."
      />
    </div>
  `,
})
export class DashboardBitacora {}
