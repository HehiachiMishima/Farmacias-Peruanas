import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PALETA_PROVEEDORES } from './constantes';

export interface DatoCol {
  etiqueta: string;
  valor: number;
}

interface Col {
  etiqueta: string;
  etiquetaCorta: string;
  valor: number;
  altura: number;
  color: string;
  titulo: string;
  destacada: boolean;
}

const ETIQUETA_MAX = 14;

function escalaBonita(max: number): number {
  if (max <= 0) return 1;
  const exp = Math.floor(Math.log10(max));
  const base = Math.pow(10, exp);
  for (const m of [1, 1.5, 2, 2.5, 3, 4, 5, 7.5, 10]) {
    const candidato = m * base;
    if (candidato >= max) return Math.ceil(candidato);
  }
  return Math.ceil(10 * base);
}

@Component({
  selector: 'ui-cols-prov',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <div class="cols">
      <span class="cols__ytitle">Total de incidencias</span>
      <div class="cols__viewport" [class.cols__viewport--scroll]="scroll()">
        <div class="cols__track" [style.min-width.px]="anchoTrack()">
          <div class="cols__plot" [style.height.px]="altura()">
            @for (t of ticks(); track t.valor) {
              <div class="gl" [style.bottom.%]="t.pct">
                <span class="gl__v">{{ t.valor | number }}</span>
              </div>
            }
            <div class="cols__barras cols__row">
              @for (c of columnas(); track c.etiqueta; let i = $index) {
                <div class="col" [attr.title]="c.titulo" [class.col--top]="c.destacada">
                  <span class="col__val" [style.--c]="c.color">{{ c.valor | number }}</span>
                  <div
                    class="col__barra"
                    [style.height.%]="c.altura"
                    [style.--c]="c.color"
                    [style.animation-delay]="Math.min(i, 40) * 35 + 'ms'"
                  ></div>
                </div>
              }
            </div>
          </div>
          <div class="cols__eje cols__row">
            @for (c of columnas(); track c.etiqueta) {
              <span class="lab">
                <span class="lab__txt" [title]="c.etiqueta">{{ c.etiquetaCorta }}</span>
              </span>
            }
          </div>
        </div>
      </div>
      @if (scroll()) {
        <p class="cols__hint">
          <span class="cols__hint-pista">↔ desliza</span>
          {{ columnas().length }} proveedores en total
        </p>
      }
    </div>
  `,
  styles: `
    :host { display: block; width: 100%; }

    .cols {
      display: flex;
      align-items: stretch;
      gap: var(--space-2);
      width: 100%;
    }

    .cols__ytitle {
      writing-mode: vertical-rl;
      transform: rotate(180deg);
      font-size: var(--fs-xs);
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--text-3);
      display: flex;
      align-items: center;
      justify-content: center;
      flex: none;
    }

    .cols__viewport {
      flex: 1;
      min-width: 0;
    }

    .cols__viewport--scroll {
      overflow-x: auto;
      overflow-y: hidden;
      padding-bottom: var(--space-2);
      overscroll-behavior-x: contain;
    }

    .cols__track {
      min-width: 100%;
    }

    .cols__plot {
      position: relative;
      background: linear-gradient(180deg, var(--surface-2), transparent 45%);
      border-left: 2px solid var(--border-strong);
      border-bottom: 2px solid var(--border-strong);
      border-radius: var(--radius-sm) var(--radius-sm) 0 0;
    }

    .gl {
      position: absolute;
      left: 54px;
      right: 0;
      border-top: 1px dashed var(--border);
      pointer-events: none;
    }

    .gl__v {
      position: absolute;
      right: calc(100% + 8px);
      top: 0;
      transform: translateY(-50%);
      font-size: var(--fs-xs);
      font-weight: 700;
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    .cols__row {
      display: flex;
      align-items: stretch;
      gap: clamp(6px, 0.8vw, 14px);
    }

    .cols__barras {
      position: absolute;
      inset: 0 0 0 54px;
      align-items: flex-end;
      padding-bottom: 4px;
    }

    .col {
      flex: 1 1 44px;
      min-width: 46px;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      gap: 4px;
      cursor: default;
    }

    .cols__viewport--scroll .col {
      flex: 0 0 58px;
      min-width: 58px;
    }

    .col__val {
      font-size: var(--fs-xs);
      font-weight: 800;
      color: var(--c, var(--text-2));
      font-variant-numeric: tabular-nums;
      line-height: 1;
      z-index: 1;
      transition: transform var(--transition);
    }

    .col__barra {
      width: min(72%, 84px);
      min-height: 5px;
      border-radius: 6px 6px 2px 2px;
      background: linear-gradient(
        180deg,
        color-mix(in srgb, var(--c) 55%, white),
        var(--c) 70%
      );
      box-shadow: 0 4px 14px color-mix(in srgb, var(--c) 32%, transparent);
      transform-origin: bottom;
      animation: col-grow 750ms var(--transition-slow) both;
      transition: filter var(--transition), transform var(--transition);
    }

    .col:hover .col__barra {
      filter: brightness(1.12) saturate(1.15);
      transform: scaleY(1.02);
    }

    .col--top .col__barra {
      box-shadow:
        0 6px 22px color-mix(in srgb, var(--c) 50%, transparent),
        0 0 0 1px color-mix(in srgb, var(--c) 45%, transparent);
    }

    .cols__eje {
      position: relative;
      height: 100px;
      align-items: flex-start;
      border-top: 2px solid var(--border-strong);
      padding-left: 54px;
    }

    .lab {
      position: relative;
      flex: 1 1 44px;
      min-width: 46px;
      height: 100%;
      display: block;
    }

    .cols__viewport--scroll .lab {
      flex: 0 0 58px;
      min-width: 58px;
    }

    .lab__txt {
      position: absolute;
      top: 6px;
      right: 50%;
      transform-origin: right top;
      transform: rotate(-45deg);
      font-size: 11px;
      font-weight: 700;
      color: var(--text-2);
      white-space: nowrap;
      max-width: 120px;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: color var(--transition);
    }

    .cols__hint {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      margin-top: var(--space-2);
      padding-left: 56px;
      font-size: var(--fs-sm);
      font-weight: 600;
      color: var(--text-3);
    }

    .cols__hint-pista {
      display: inline-flex;
      align-items: center;
      padding: 4px 12px;
      border-radius: var(--radius-pill);
      background: var(--brand-soft);
      color: var(--text-2);
      font-weight: 800;
      animation: hint-slide 1.8s ease-in-out infinite;
    }

    @keyframes col-grow {
      from { transform: scaleY(0); opacity: 0.35; }
      to { transform: scaleY(1); opacity: 1; }
    }

    @keyframes hint-slide {
      0%, 100% { transform: translateX(0); }
      50% { transform: translateX(6px); }
    }

    @media (prefers-reduced-motion: reduce) {
      .col__barra, .cols__hint-pista { animation: none; }
    }
  `,
})
export class ColumnasProveedores {
  readonly datos = input<DatoCol[]>([]);
  readonly altura = input<number>(500);
  readonly scroll = input<boolean>(false);

  readonly Math = Math;

  readonly columnas = computed<Col[]>(() => {
    const datos = this.datos();
    const suma = datos.reduce((t, d) => t + d.valor, 0) || 1;
    const maxEscala = escalaBonita(Math.max(1, ...datos.map((d) => d.valor)));
    return datos.map((d, i) => ({
      etiqueta: d.etiqueta,
      etiquetaCorta: d.etiqueta.length > ETIQUETA_MAX ? d.etiqueta.slice(0, ETIQUETA_MAX - 1) + '…' : d.etiqueta,
      valor: d.valor,
      altura: Math.max(1.5, (d.valor / maxEscala) * 100),
      color: PALETA_PROVEEDORES[i % PALETA_PROVEEDORES.length],
      destacada: i < 3,
      titulo: `${d.etiqueta}: ${d.valor} incidencias (${((d.valor / suma) * 100).toFixed(1)}% del total)`,
    }));
  });

  readonly ticks = computed(() => {
    const datos = this.datos();
    const maxEscala = escalaBonita(Math.max(1, ...datos.map((d) => d.valor)));
    return [0, 0.25, 0.5, 0.75, 1].map((f) => ({ valor: Math.round(maxEscala * f), pct: f * 100 }));
  });

  readonly anchoTrack = computed(() => (this.scroll() ? this.columnas().length * 70 : 0));
}
