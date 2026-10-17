import { ChangeDetectionStrategy, Component, afterNextRender, computed, input, signal } from '@angular/core';

export interface SegmentoDonut {
  etiqueta: string;
  valor: number;
  color: string;
}

interface ArcoDonut {
  etiqueta: string;
  valor: number;
  pct: number;
  color: string;
  dash: string;
  offset: number;
}

@Component({
  selector: 'ui-donut',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="donut">
      <div class="donut__ring" [style.max-width.px]="tamano()">
        <svg viewBox="0 0 42 42" role="img" [attr.aria-label]="titulo()">
          <circle class="donut__track" cx="21" cy="21" r="15.9155" />
          @for (arco of arcos(); track arco.etiqueta) {
            <circle
              class="donut__arc"
              cx="21"
              cy="21"
              r="15.9155"
              [attr.stroke-dasharray]="arco.dash"
              [attr.stroke-dashoffset]="arco.offset"
              [style.stroke]="arco.color"
            />
          }
        </svg>
        <div class="donut__center">
          <span class="donut__total">{{ total() }}</span>
          <span class="donut__total-label">{{ centro() }}</span>
        </div>
      </div>

      <ul class="donut__legend">
        @for (arco of arcos(); track arco.etiqueta) {
          <li class="donut__legend-item">
            <span class="donut__swatch" [style.background]="arco.color"></span>
            <span class="donut__legend-label">{{ arco.etiqueta }}</span>
            <span class="donut__legend-value">{{ arco.valor }}</span>
            <span class="donut__legend-pct">{{ arco.pct }}%</span>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    :host { display: block; }

    .donut {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-5);
    }

    .donut__ring {
      position: relative;
      width: 100%;
      aspect-ratio: 1;
      animation: donut-in 900ms var(--transition-slow) both;
    }

    svg {
      width: 100%;
      height: 100%;
      transform: rotate(-90deg);
    }

    .donut__track {
      fill: none;
      stroke: var(--surface-3);
      stroke-width: 4.4;
    }

    .donut__arc {
      fill: none;
      stroke-width: 4.4;
      stroke-linecap: round;
      transition: stroke-dasharray 900ms var(--transition-slow);
    }

    .donut__center {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 2px;
      text-align: center;
    }

    .donut__total {
      font-size: var(--fs-3xl);
      font-weight: 600;
      line-height: 1;
      letter-spacing: -0.03em;
      color: var(--text);
    }

    .donut__total-label {
      font-size: var(--fs-xs);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--text-3);
    }

    .donut__legend {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      width: 100%;
    }

    .donut__legend-item {
      display: grid;
      grid-template-columns: auto 1fr auto auto;
      align-items: center;
      gap: var(--space-3);
      font-size: var(--fs-sm);
    }

    .donut__swatch {
      width: 10px;
      height: 10px;
      border-radius: 50%;
    }

    .donut__legend-label { color: var(--text-2); }
    .donut__legend-value { font-weight: 600; color: var(--text); }
    .donut__legend-pct {
      min-width: 3ch;
      text-align: right;
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
    }

    @keyframes donut-in {
      from { opacity: 0; transform: scale(0.85); }
      to { opacity: 1; transform: scale(1); }
    }
  `,
})
export class DonutChart {
  readonly segmentos = input<SegmentoDonut[]>([]);
  readonly centro = input<string>('Total');
  readonly titulo = input<string>('Distribución');
  readonly tamano = input<number>(220);

  private readonly listo = signal(false);

  constructor() {
    afterNextRender(() => this.listo.set(true));
  }

  readonly total = computed(() =>
    this.segmentos().reduce((acc, segmento) => acc + Math.max(0, segmento.valor), 0),
  );

  readonly arcos = computed<ArcoDonut[]>(() => {
    const segmentos = this.segmentos().filter((segmento) => segmento.valor > 0);
    const total = segmentos.reduce((acc, segmento) => acc + segmento.valor, 0);
    let acumulado = 0;

    return segmentos.map((segmento) => {
      const pct = total > 0 ? (segmento.valor / total) * 100 : 0;
      const offset = -acumulado;
      acumulado += pct;
      return {
        etiqueta: segmento.etiqueta,
        valor: segmento.valor,
        color: segmento.color,
        pct: Math.round(pct),
        dash: this.listo() ? `${pct} ${100 - pct}` : '0 100',
        offset,
      };
    });
  });
}
