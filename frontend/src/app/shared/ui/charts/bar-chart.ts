import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface BarraDato {
  etiqueta: string;
  valor: number;
  color: string;
}

@Component({
  selector: 'ui-bars',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="bars" [style.height.px]="altura()">
      @for (barra of barras(); track barra.etiqueta; let i = $index) {
        <div class="bars__col">
          <span class="bars__value">{{ barra.valor }}</span>
          <div class="bars__track">
            <div
              class="bars__fill"
              [style.height.%]="barra.pct"
              [style.background]="barra.color"
              [style.animation-delay]="i * 90 + 'ms'"
            ></div>
          </div>
          <span class="bars__label">{{ barra.etiqueta }}</span>
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; }

    .bars {
      display: flex;
      align-items: stretch;
      gap: clamp(var(--space-3), 3vw, var(--space-6));
      padding-top: var(--space-5);
    }

    .bars__col {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-3);
    }

    .bars__value {
      font-size: var(--fs-lg);
      font-weight: 600;
      color: var(--text);
      font-variant-numeric: tabular-nums;
    }

    .bars__track {
      flex: 1;
      width: 100%;
      max-width: 68px;
      display: flex;
      align-items: flex-end;
      border-radius: var(--radius);
      background: var(--surface-2);
      overflow: hidden;
    }

    .bars__fill {
      width: 100%;
      border-radius: var(--radius);
      transform-origin: bottom;
      animation: bar-grow 900ms var(--transition-slow) both;
    }

    .bars__label {
      font-size: var(--fs-sm);
      font-weight: 600;
      color: var(--text-2);
      text-align: center;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    @keyframes bar-grow {
      from { transform: scaleY(0); opacity: 0.4; }
      to { transform: scaleY(1); opacity: 1; }
    }
  `,
})
export class BarChart {
  readonly datos = input<BarraDato[]>([]);
  readonly altura = input<number>(220);

  readonly barras = computed(() => {
    const datos = this.datos();
    const maximo = Math.max(1, ...datos.map((dato) => dato.valor));
    return datos.map((dato) => ({
      ...dato,
      pct: dato.valor <= 0 ? 0 : Math.max(6, (dato.valor / maximo) * 100),
    }));
  });
}
