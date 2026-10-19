import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ProveedorMensualEstadistica } from '../../../core/models';

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic'];

export const PALETA_PROVEEDORES = [
  '#6366f1',
  '#0ea5e9',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#ec4899',
  '#8b5cf6',
  '#14b8a6',
  '#f97316',
  '#84cc16',
];

interface FilaRanking {
  posicion: number;
  proveedor: string;
  total: number;
  pct: number;
  color: string;
  meses: { etiqueta: string; valor: number; altura: number; titulo: string }[];
}

@Component({
  selector: 'ui-rank-prov',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <div class="rank">
      <div class="rank__head">
        <span class="rank__h-pos">#</span>
        <span class="rank__h-nombre">Proveedor</span>
        <span class="rank__h-meses">Incidencias por mes</span>
        <span class="rank__h-total">Total</span>
        <span class="rank__h-pct">Part.</span>
      </div>
      @for (f of filas(); track f.posicion; let i = $index) {
        <div class="fila" [style.--f-color]="f.color" [style.animation-delay]="i * 60 + 'ms'">
          <span class="fila__pos">{{ f.posicion }}</span>
          <span class="fila__nombre" [title]="f.proveedor">{{ f.proveedor }}</span>
          <div class="fila__meses">
            @for (m of f.meses; track m.etiqueta; let j = $index) {
              <div class="mes" [attr.title]="m.titulo">
                <span class="mes__valor">{{ m.valor }}</span>
                <div class="mes__col">
                  <div
                    class="mes__barra"
                    [style.height.%]="m.altura"
                    [style.animation-delay]="i * 60 + Math.min(j, 24) * 35 + 'ms'"
                  ></div>
                </div>
                <span class="mes__label">{{ m.etiqueta }}</span>
              </div>
            }
          </div>
          <span class="fila__total">{{ f.total | number }}</span>
          <span class="fila__pct">{{ f.pct | number: '1.1-1' }}%</span>
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; width: 100%; }

    .rank {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      width: 100%;
    }

    .rank__head,
    .fila {
      display: grid;
      grid-template-columns: 44px minmax(150px, 220px) 1fr 104px 78px;
      gap: var(--space-4);
      align-items: center;
    }

    .rank__head {
      padding: 0 var(--space-4) var(--space-2);
      font-size: var(--fs-xs);
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--text-3);
    }

    .rank__h-total, .rank__h-pct { text-align: right; }

    .fila {
      padding: var(--space-3) var(--space-4);
      background: var(--surface);
      border: 1px solid var(--border);
      border-left: 4px solid var(--f-color);
      border-radius: var(--radius-xl);
      animation: rank-in var(--transition-slow) both;
      transition: border-color var(--transition), box-shadow var(--transition), transform var(--transition);
    }

    .fila:hover {
      border-color: color-mix(in srgb, var(--f-color) 50%, var(--border));
      border-left-color: var(--f-color);
      box-shadow: 0 6px 20px color-mix(in srgb, var(--f-color) 16%, transparent);
      transform: translateX(4px);
    }

    .fila__pos {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: var(--radius-pill);
      background: color-mix(in srgb, var(--f-color) 14%, transparent);
      color: var(--f-color);
      font-size: var(--fs-sm);
      font-weight: 800;
      font-variant-numeric: tabular-nums;
    }

    .fila__nombre {
      font-size: var(--fs-md);
      font-weight: 600;
      color: var(--text);
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      line-height: 1.25;
    }

    .fila__meses {
      display: flex;
      align-items: flex-end;
      justify-content: center;
      gap: clamp(6px, 1vw, 14px);
      min-width: 0;
    }

    .mes {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 3px;
      flex: 0 1 56px;
      min-width: 30px;
      cursor: default;
    }

    .mes__valor {
      font-size: var(--fs-xs);
      font-weight: 700;
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
      transition: color var(--transition);
    }

    .mes:hover .mes__valor { color: var(--f-color); }

    .mes__col {
      width: 100%;
      height: 54px;
      display: flex;
      align-items: flex-end;
      background: linear-gradient(180deg, transparent, color-mix(in srgb, var(--f-color) 5%, transparent));
      border-radius: var(--radius-sm);
      padding: 0 15%;
    }

    .mes__barra {
      width: 100%;
      min-height: 4px;
      border-radius: 3px;
      background: linear-gradient(
        180deg,
        color-mix(in srgb, var(--f-color) 62%, white),
        var(--f-color)
      );
      box-shadow: 0 2px 8px color-mix(in srgb, var(--f-color) 30%, transparent);
      transform-origin: bottom;
      animation: rank-grow 700ms var(--transition-slow) both;
      transition: filter var(--transition);
    }

    .mes:hover .mes__barra { filter: brightness(1.15); }

    .mes__label {
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--text-3);
    }

    .fila__total {
      text-align: right;
      font-size: clamp(var(--fs-xl), 1.6vw, var(--fs-2xl));
      font-weight: 800;
      letter-spacing: -0.02em;
      color: var(--f-color);
      font-variant-numeric: tabular-nums;
    }

    .fila__pct {
      text-align: right;
      font-size: var(--fs-md);
      font-weight: 700;
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
    }

    @keyframes rank-in {
      from { opacity: 0; transform: translateX(-14px); }
      to { opacity: 1; transform: translateX(0); }
    }

    @keyframes rank-grow {
      from { transform: scaleY(0); opacity: 0.4; }
      to { transform: scaleY(1); opacity: 1; }
    }

    @media (prefers-reduced-motion: reduce) {
      .fila, .mes__barra { animation: none; }
    }

    @media (max-width: 900px) {
      .rank__head { display: none; }
      .fila {
        grid-template-columns: 44px 1fr;
        grid-template-areas: 'pos nombre' 'meses meses' 'total pct';
      }
      .fila__pos { grid-area: pos; }
      .fila__nombre { grid-area: nombre; }
      .fila__meses { grid-area: meses; flex-wrap: wrap; }
      .fila__total { grid-area: total; text-align: left; }
      .fila__pct { grid-area: pct; }
    }
  `,
})
export class RankingProveedores {
  readonly datos = input<ProveedorMensualEstadistica[]>([]);
  readonly totalGeneral = input<number>(0);

  readonly Math = Math;

  readonly filas = computed<FilaRanking[]>(() => {
    const datos = this.datos();
    const totalGeneral = this.totalGeneral() || 1;
    const maximo = Math.max(1, ...datos.flatMap((d) => d.meses.map((m) => m.incidencias)));
    return datos.map((d, i) => ({
      posicion: i + 1,
      proveedor: d.proveedor,
      total: d.total,
      pct: (d.total / totalGeneral) * 100,
      color: PALETA_PROVEEDORES[i % PALETA_PROVEEDORES.length],
      meses: d.meses.map((m) => ({
        etiqueta: this.nombreMes(m.mes),
        valor: m.incidencias,
        altura: Math.max(6, Math.round((m.incidencias / maximo) * 100)),
        titulo: `${this.nombreMesCompleto(m.mes)}: ${m.incidencias} ${m.incidencias === 1 ? 'incidencia' : 'incidencias'}`,
      })),
    }));
  });

  private nombreMes(mes: string): string {
    return MESES[Number(mes.split('-')[1]) - 1] ?? mes;
  }

  private nombreMesCompleto(mes: string): string {
    const [anio, num] = mes.split('-');
    return `${this.nombreMes(mes)} ${anio.slice(2)}`;
  }
}
