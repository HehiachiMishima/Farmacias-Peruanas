import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { entero, porcentaje } from './formato';

export interface ItemPareto {
  etiqueta: string;
  valor: number;
  /** Texto secundario opcional (p. ej. «1 240 u · tipo dominante: FALTANTE»). */
  detalle?: string;
}

/**
 * Pareto horizontal: ranking ordenado, barra proporcional y % acumulado.
 * Los ítems que juntos alcanzan el `umbral` (80 % por defecto) se marcan como
 * «vitales»: son los que conviene atacar primero.
 */
@Component({
  selector: 'ui-pareto',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pa">
      <div class="pa__head" aria-hidden="true">
        <span>#</span><span>Elemento</span><span class="pa__num">Total</span><span class="pa__num">% acum.</span>
      </div>
      <ol class="pa__lista">
        @for (f of filas(); track f.etiqueta; let i = $index) {
          <li class="pa__fila" [class.pa__fila--vital]="f.vital" [style.animation-delay]="i * 40 + 'ms'">
            <span class="pa__rank">{{ f.otros ? '…' : i + 1 }}</span>
            <span class="pa__cuerpo">
              <span class="pa__label" [title]="f.etiqueta">{{ f.etiqueta }}</span>
              <span class="pa__barra"><span [style.width.%]="f.pct"></span></span>
              @if (f.detalle) {
                <span class="pa__detalle">{{ f.detalle }}</span>
              }
            </span>
            <span class="pa__num pa__valor">{{ f.texto }}</span>
            <span class="pa__num pa__acum">
              <span class="pa__acum-bar"><span [style.width.%]="f.acumulado * 100"></span></span>
              {{ f.textoAcum }}
            </span>
          </li>
        }
      </ol>
      @if (vitales() > 0) {
        <p class="pa__nota">
          <strong>{{ vitales() }}</strong> de {{ totalItems() }} concentran el
          <strong>{{ porcentajeVital() }}</strong> del total.
        </p>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .pa__head, .pa__fila {
      display: grid;
      grid-template-columns: 28px minmax(0, 1fr) 64px 96px;
      align-items: center;
      gap: var(--space-3);
    }
    .pa__head {
      padding: 0 var(--space-2) var(--space-2);
      font-size: 0.66rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--text-3);
      border-bottom: 1px solid var(--border);
    }
    .pa__lista { display: flex; flex-direction: column; }
    .pa__fila {
      padding: 8px var(--space-2);
      border-bottom: 1px solid var(--border);
      animation: ui-rise var(--transition-slow) both;
    }
    .pa__fila:last-child { border-bottom: 0; }
    .pa__rank {
      display: inline-flex; align-items: center; justify-content: center;
      width: 24px; height: 24px; border-radius: var(--radius-xs);
      font-size: var(--fs-xs); font-weight: 800; color: var(--text-3); background: var(--surface-3);
    }
    .pa__fila--vital .pa__rank { color: #fff; background: var(--brand); }
    .pa__cuerpo { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    .pa__label { font-size: var(--fs-sm); font-weight: 700; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .pa__barra { height: 8px; border-radius: var(--radius-pill); background: var(--surface-3); overflow: hidden; }
    .pa__barra > span {
      display: block; height: 100%; border-radius: inherit;
      background: linear-gradient(90deg, color-mix(in srgb, var(--accent) 55%, transparent), var(--accent));
      transform-origin: left; animation: pa-crece 800ms cubic-bezier(0.16, 1, 0.3, 1) both;
    }
    .pa__fila--vital .pa__barra > span { background: linear-gradient(90deg, color-mix(in srgb, var(--brand) 55%, transparent), var(--brand)); }
    .pa__detalle { font-size: 0.7rem; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .pa__num { text-align: right; font-variant-numeric: tabular-nums; }
    .pa__valor { font-size: var(--fs-sm); font-weight: 800; color: var(--text); }
    .pa__acum { display: flex; align-items: center; justify-content: flex-end; gap: 6px; font-size: var(--fs-xs); font-weight: 600; color: var(--text-2); }
    .pa__acum-bar { width: 40px; height: 4px; border-radius: 4px; background: var(--surface-3); overflow: hidden; }
    .pa__acum-bar > span { display: block; height: 100%; background: var(--warning); }
    .pa__nota { margin-top: var(--space-3); font-size: var(--fs-sm); color: var(--text-2); }
    .pa__nota strong { color: var(--text); }
    @keyframes pa-crece { from { transform: scaleX(0); } to { transform: scaleX(1); } }
    @media (max-width: 480px) {
      .pa__head, .pa__fila { grid-template-columns: 24px minmax(0, 1fr) 52px; }
      .pa__head span:last-child, .pa__acum { display: none; }
    }
  `,
})
export class ParetoChart {
  readonly items = input<ItemPareto[]>([]);
  /** Filas visibles; el resto se agrupa en «Otros». */
  readonly maximo = input<number>(12);
  readonly umbral = input<number>(0.8);

  private readonly ordenados = computed(() => [...this.items()].sort((a, b) => b.valor - a.valor));
  private readonly total = computed(() => this.ordenados().reduce((t, i) => t + i.valor, 0));
  readonly totalItems = computed(() => this.ordenados().length);

  readonly filas = computed(() => {
    const total = this.total() || 1;
    const lista = this.ordenados();
    const visibles = lista.slice(0, this.maximo());
    const resto = lista.slice(this.maximo());
    const tope = visibles[0]?.valor || 1;
    let acumulado = 0;
    let vitalCerrado = false;
    const filas = visibles.map((item) => {
      acumulado += item.valor;
      const frac = acumulado / total;
      const vital = !vitalCerrado;
      if (frac >= this.umbral()) {
        vitalCerrado = true;
      }
      return {
        etiqueta: item.etiqueta,
        detalle: item.detalle,
        pct: (item.valor / tope) * 100,
        texto: entero(item.valor),
        acumulado: frac,
        textoAcum: porcentaje(frac),
        vital,
        otros: false,
      };
    });
    if (resto.length > 0) {
      const suma = resto.reduce((t, i) => t + i.valor, 0);
      acumulado += suma;
      filas.push({
        etiqueta: `Otros (${resto.length})`,
        detalle: undefined,
        pct: (suma / tope) * 100,
        texto: entero(suma),
        acumulado: acumulado / total,
        textoAcum: porcentaje(acumulado / total),
        vital: false,
        otros: true,
      });
    }
    return filas;
  });

  readonly vitales = computed(() => this.filas().filter((f) => f.vital).length);

  readonly porcentajeVital = computed(() => {
    const vitales = this.filas().filter((f) => f.vital);
    return porcentaje(vitales.length ? vitales[vitales.length - 1].acumulado : 0);
  });
}
