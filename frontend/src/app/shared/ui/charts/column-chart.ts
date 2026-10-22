import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { entero } from './formato';

export interface Columna {
  etiqueta: string;
  valor: number;
}

/**
 * Columnas verticales compactas (muchas categorías: 24 horas, 12 meses…).
 * El pico se resalta con el color de marca; las columnas se reparten el ancho
 * disponible y, si no caben, el contenedor se desplaza en horizontal.
 */
@Component({
  selector: 'ui-columnas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="co" [style.--alto.px]="altura()" [style.--min.px]="anchoMinimo()">
      <div class="co__scroll">
        <div class="co__cols">
          @for (c of columnas(); track c.etiqueta; let i = $index) {
            <div class="co__col" [class.co__col--pico]="c.pico" [attr.title]="c.etiqueta + ': ' + c.texto">
              <span class="co__valor">{{ c.valor > 0 && (columnas().length <= 12 || c.pico) ? c.texto : '' }}</span>
              <span class="co__pista">
                <span class="co__barra" [style.height.%]="c.pct" [style.animation-delay]="i * 25 + 'ms'"></span>
              </span>
              <span class="co__label">{{ i % cadaEtiqueta() === 0 ? c.etiqueta : '' }}</span>
            </div>
          }
        </div>
      </div>
      @if (pico(); as p) {
        <p class="co__nota">Pico: <strong>{{ prefijoPico() }}{{ p.etiqueta }}</strong> con <strong>{{ p.texto }}</strong>.</p>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .co__scroll { overflow-x: auto; }
    .co__cols { display: flex; align-items: stretch; gap: 3px; min-width: calc(var(--min) * var(--n, 1)); height: calc(var(--alto) + 40px); }
    .co__col { flex: 1 1 0; min-width: var(--min); display: flex; flex-direction: column; align-items: center; gap: 4px; }
    .co__valor { height: 14px; font-size: 0.6rem; font-weight: 700; color: var(--text-3); font-variant-numeric: tabular-nums; }
    .co__pista { flex: 1; width: 100%; display: flex; align-items: flex-end; border-radius: 4px; background: var(--surface-2); }
    .co__barra {
      display: block; width: 100%; border-radius: 4px 4px 2px 2px;
      background: linear-gradient(180deg, var(--accent), color-mix(in srgb, var(--accent) 55%, transparent));
      transform-origin: bottom; animation: co-crece 700ms cubic-bezier(0.16, 1, 0.3, 1) both;
    }
    .co__col--pico .co__barra { background: linear-gradient(180deg, var(--brand), color-mix(in srgb, var(--brand) 55%, transparent)); }
    .co__col--pico .co__valor { color: var(--brand); }
    .co__label { font-size: 0.64rem; font-weight: 600; color: var(--text-3); white-space: nowrap; }
    .co__nota { margin-top: var(--space-2); font-size: var(--fs-sm); color: var(--text-2); }
    .co__nota strong { color: var(--text); }
    @keyframes co-crece { from { transform: scaleY(0); } to { transform: scaleY(1); } }
  `,
  host: { '[style.--n]': 'columnas().length' },
})
export class ColumnChart {
  readonly datos = input<Columna[]>([]);
  readonly altura = input<number>(160);
  readonly anchoMinimo = input<number>(22);
  /** Texto antepuesto a la etiqueta del pico (p. ej. «las » para «las 08 h»). */
  readonly prefijoPico = input<string>('');
  /** Muestra la etiqueta del eje solo cada N columnas (p. ej. 3 para horas). */
  readonly cadaEtiqueta = input<number>(1);

  private readonly maximo = computed(() => Math.max(1, ...this.datos().map((d) => d.valor)));

  readonly columnas = computed(() => {
    const max = this.maximo();
    const picoValor = Math.max(0, ...this.datos().map((d) => d.valor));
    return this.datos().map((d) => ({
      ...d,
      pct: (d.valor / max) * 100,
      texto: entero(d.valor),
      pico: d.valor > 0 && d.valor === picoValor,
    }));
  });

  readonly pico = computed(() => this.columnas().find((c) => c.pico) ?? null);
}
