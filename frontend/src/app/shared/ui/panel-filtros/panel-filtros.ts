import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Icon } from '../icon/icon';

/**
 * Panel lateral de filtros (drawer derecho).
 *
 * Reutiliza la misma mecánica de ocultar/mostrar del sidebar "Módulos" del
 * panel shell: se desliza fuera de pantalla, con velo de fondo, y se toglea
 * desde un botón externo. El contenido de los filtros se proyecta por
 * `ng-content`, de modo que cada módulo define sus propios campos con su
 * propio diseño.
 */
@Component({
  selector: 'ui-panel-filtros',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div class="ui-panel-filtros" [class.ui-panel-filtros--abierto]="abierto()">
      <div class="ui-panel-filtros__overlay" (click)="cerrar.emit()"></div>

      <aside
        class="ui-panel-filtros__panel"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="titulo()"
        [attr.inert]="abierto() ? null : ''"
      >
        <header class="ui-panel-filtros__header">
          <span class="ui-panel-filtros__title">
            <ui-icon name="filtro" [size]="16" />
            {{ titulo() }}
          </span>
        </header>

        <div class="ui-panel-filtros__body">
          <ng-content />
        </div>
      </aside>
    </div>
  `,
})
export class PanelFiltros {
  readonly abierto = input(false);
  readonly titulo = input('Filtros');
  readonly cerrar = output<void>();
}
