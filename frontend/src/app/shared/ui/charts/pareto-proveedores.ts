import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

export interface DatoPareto {
  etiqueta: string;
  valor: number;
}

interface BarraPareto {
  x: number;
  y: number;
  w: number;
  h: number;
  titulo: string;
  retardo: number;
}

const W = 1000;
const H = 360;
const IZQ = 52;
const DER = 18;
const ARR = 22;
const BASE = 52;
const AREA_W = W - IZQ - DER;
const AREA_H = H - ARR - BASE;

@Component({
  selector: 'ui-pareto',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <div class="pareto">
      <div class="pareto__mets">
        <div class="met">
          <span class="met__valor">{{ nProveedores() | number }}</span>
          <span class="met__label">proveedores con incidencias</span>
        </div>
        <div class="met met--marca">
          <span class="met__valor">{{ cruce80() }}</span>
          <span class="met__label">generan el 80% de las incidencias</span>
        </div>
        <div class="met">
          <span class="met__valor">{{ pctTop10() | number: '1.1-1' }}%</span>
          <span class="met__label">lo concentra el Top 10</span>
        </div>
      </div>

      <div class="pareto__svg">
        <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="none" role="img"
             aria-label="Diagrama de Pareto de incidencias por proveedor">
          @for (g of grid(); track g.p) {
            <line class="grid" [attr.x1]="IZQ" [attr.x2]="W - DER" [attr.y1]="g.y" [attr.y2]="g.y" />
            <text class="eje" [attr.x]="IZQ - 10" [attr.y]="g.y + 4" text-anchor="end">{{ g.p }}%</text>
          }

          <line class="linea80" [attr.x1]="IZQ" [attr.x2]="W - DER" [attr.y1]="y80" [attr.y2]="y80" />

          @for (b of barras(); track $index) {
            <rect
              class="barra"
              [attr.x]="b.x"
              [attr.y]="b.y"
              [attr.width]="b.w"
              [attr.height]="b.h"
              [style.animation-delay]="b.retardo + 'ms'"
            >
              <title>{{ b.titulo }}</title>
            </rect>
          }

          <polyline class="curva" [attr.points]="curva()" />

          @if (puntoCruce(); as pc) {
            <line class="cruce" [attr.x1]="pc.x" [attr.x2]="pc.x" [attr.y1]="pc.y" [attr.y2]="H - BASE" />
            <circle class="cruce__pt" [attr.cx]="pc.x" [attr.cy]="pc.y" r="6" />
            <text
              class="cruce__txt"
              [attr.x]="pc.tx"
              [attr.y]="pc.ty"
              [attr.text-anchor]="pc.anchor"
            >
              {{ pc.texto }}
            </text>
          }

          <text class="eje eje--tit" [attr.x]="IZQ + AREA_W / 2" [attr.y]="H - 12" text-anchor="middle">
            Proveedores ordenados por nº de incidencias (de mayor a menor)
          </text>
          <text
            class="eje eje--tit"
            [attr.x]="-(ARR + AREA_H / 2)"
            [attr.y]="14"
            text-anchor="middle"
            [attr.transform]="'rotate(-90)'"
          >
            % acumulado de incidencias
          </text>
        </svg>
      </div>

      <p class="pareto__leyenda">
        <span class="ley ley--barra"></span> Incidencias del proveedor
        <span class="ley ley--curva"></span> % acumulado
        <span class="ley ley--80"></span> Umbral de concentración 80%
      </p>
    </div>
  `,
  styles: `
    :host { display: block; width: 100%; }

    .pareto {
      display: flex;
      flex-direction: column;
      gap: var(--space-5);
      width: 100%;
    }

    .pareto__mets {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-5);
      justify-content: space-around;
      padding: var(--space-4) var(--space-5);
      background: var(--surface-2);
      border-radius: var(--radius-xl);
    }

    .met {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
      text-align: center;
    }

    .met__valor {
      font-size: clamp(var(--fs-2xl), 3vw, var(--fs-3xl));
      font-weight: 800;
      letter-spacing: -0.03em;
      line-height: 1;
      color: var(--text);
      font-variant-numeric: tabular-nums;
    }

    .met--marca .met__valor { color: var(--danger); }

    .met__label {
      font-size: var(--fs-sm);
      font-weight: 600;
      color: var(--text-3);
      max-width: 220px;
    }

    .pareto__svg {
      width: 100%;
      height: clamp(340px, 34vw, 460px);
    }

    svg { width: 100%; height: 100%; overflow: visible; }

    .grid { stroke: var(--border); stroke-width: 1; }

    .eje {
      font-size: 11px;
      font-weight: 600;
      fill: var(--text-3);
    }

    .eje--tit {
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .linea80 {
      stroke: var(--danger);
      stroke-width: 1.5;
      stroke-dasharray: 7 5;
      opacity: 0.75;
    }

    .barra {
      fill: var(--brand);
      opacity: 0.72;
      transform-box: fill-box;
      transform-origin: bottom;
      animation: pareto-grow 650ms var(--transition-slow) both;
      transition: opacity var(--transition), fill var(--transition);
      cursor: default;
    }

    .barra:hover {
      opacity: 1;
      fill: color-mix(in srgb, var(--brand) 70%, var(--danger));
    }

    .curva {
      fill: none;
      stroke: var(--warning);
      stroke-width: 3;
      stroke-linejoin: round;
      stroke-linecap: round;
      filter: drop-shadow(0 2px 6px color-mix(in srgb, var(--warning) 45%, transparent));
    }

    .cruce {
      stroke: var(--danger);
      stroke-width: 1.5;
      stroke-dasharray: 4 4;
      opacity: 0.7;
    }

    .cruce__pt {
      fill: var(--danger);
      stroke: var(--surface);
      stroke-width: 2.5;
    }

    .cruce__txt {
      font-size: 13px;
      font-weight: 800;
      fill: var(--danger);
      letter-spacing: -0.01em;
    }

    .pareto__leyenda {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: var(--space-5);
      font-size: var(--fs-sm);
      font-weight: 600;
      color: var(--text-3);
    }

    .ley {
      display: inline-block;
      width: 22px;
      height: 10px;
      border-radius: 3px;
      margin-right: var(--space-2);
      vertical-align: middle;
    }

    .ley--barra { background: var(--brand); opacity: 0.72; }
    .ley--curva { background: var(--warning); height: 4px; border-radius: 4px; }
    .ley--80 { background: repeating-linear-gradient(90deg, var(--danger) 0 6px, transparent 6px 11px); }

    @keyframes pareto-grow {
      from { transform: scaleY(0); opacity: 0.3; }
      to { transform: scaleY(1); }
    }

    @media (prefers-reduced-motion: reduce) {
      .barra { animation: none; }
    }
  `,
})
export class ParetoProveedores {
  readonly datos = input<DatoPareto[]>([]);

  readonly W = W;
  readonly H = H;
  readonly IZQ = IZQ;
  readonly DER = DER;
  readonly ARR = ARR;
  readonly BASE = BASE;
  readonly AREA_W = AREA_W;
  readonly AREA_H = AREA_H;
  readonly y80 = ARR + AREA_H * 0.2;

  readonly nProveedores = computed(() => this.datos().length);

  readonly cruce80 = computed(() => {
    const datos = this.datos();
    const suma = datos.reduce((t, d) => t + d.valor, 0);
    if (suma <= 0) return 0;
    let acum = 0;
    for (let i = 0; i < datos.length; i++) {
      acum += datos[i].valor;
      if (acum / suma >= 0.8) return i + 1;
    }
    return datos.length;
  });

  readonly pctTop10 = computed(() => {
    const datos = this.datos();
    const suma = datos.reduce((t, d) => t + d.valor, 0);
    if (suma <= 0) return 0;
    return (datos.slice(0, 10).reduce((t, d) => t + d.valor, 0) / suma) * 100;
  });

  readonly grid = computed(() =>
    [0, 25, 50, 75, 100].map((p) => ({ p, y: ARR + AREA_H * (1 - p / 100) })),
  );

  readonly barras = computed<BarraPareto[]>(() => {
    const datos = this.datos();
    if (datos.length === 0) return [];
    const maximo = Math.max(...datos.map((d) => d.valor), 1);
    const step = AREA_W / datos.length;
    const w = Math.max(1.2, step - Math.min(2, step * 0.22));
    const suma = datos.reduce((t, d) => t + d.valor, 0) || 1;
    let acum = 0;
    return datos.map((d, i) => {
      acum += d.valor;
      const h = Math.max(1, (d.valor / maximo) * AREA_H);
      return {
        x: IZQ + i * step + (step - w) / 2,
        y: ARR + AREA_H - h,
        w,
        h,
        retardo: Math.min(i, 40) * 18,
        titulo: `${d.etiqueta}: ${d.valor} incidencias (${((d.valor / suma) * 100).toFixed(1)}%) · acumulado ${((acum / suma) * 100).toFixed(1)}%`,
      };
    });
  });

  readonly curva = computed(() => {
    const datos = this.datos();
    if (datos.length === 0) return '';
    const step = AREA_W / datos.length;
    const suma = datos.reduce((t, d) => t + d.valor, 0) || 1;
    let acum = 0;
    return datos
      .map((d, i) => {
        acum += d.valor;
        const x = IZQ + i * step + step / 2;
        const y = ARR + AREA_H * (1 - acum / suma);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  });

  readonly puntoCruce = computed(() => {
    const datos = this.datos();
    const n = this.cruce80();
    if (datos.length === 0 || n <= 0) return null;
    const step = AREA_W / datos.length;
    const x = IZQ + (n - 1) * step + step / 2;
    const y = this.y80;
    const aDerecha = x < W * 0.62;
    return {
      x,
      y,
      tx: aDerecha ? x + 14 : x - 14,
      ty: y - 14,
      anchor: aDerecha ? 'start' : 'end',
      texto: `${n} proveedores = 80%`,
    };
  });
}
