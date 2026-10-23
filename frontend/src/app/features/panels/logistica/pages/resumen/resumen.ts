import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';

@Component({
  selector: 'app-logistica-resumen',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, EmptyState],
  template: `
    <header class="page-head">
      <div>
        <h1 class="page-head__title">Logística</h1>
        <p class="page-head__sub">Gestión de las herramientas y procesos de la operación.</p>
      </div>
      <span class="ui-badge ui-badge--warning">
        <ui-icon name="reloj" [size]="14" />
        En construcción
      </span>
    </header>

    <div class="table-card">
      <ui-empty
        icono="logistica"
        titulo="Panel en construcción"
        mensaje="Este panel todavía no tiene módulos con datos. Estará disponible próximamente."
      ></ui-empty>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .page-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--space-4);
    }
  `,
})
export class LogisticaResumen {}
