import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export interface RangoFechas {
  desde: string;
  hasta: string;
}

interface Opcion {
  id: string;
  etiqueta: string;
  rango: () => RangoFechas;
}

function iso(fecha: Date): string {
  const mes = `${fecha.getMonth() + 1}`.padStart(2, '0');
  const dia = `${fecha.getDate()}`.padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

function haceDias(dias: number): RangoFechas {
  const hoy = new Date();
  const desde = new Date(hoy);
  desde.setDate(hoy.getDate() - (dias - 1));
  return { desde: iso(desde), hasta: iso(hoy) };
}

const OPCIONES: Opcion[] = [
  { id: 'hoy', etiqueta: 'Hoy', rango: () => haceDias(1) },
  { id: '7d', etiqueta: '7 días', rango: () => haceDias(7) },
  { id: '30d', etiqueta: '30 días', rango: () => haceDias(30) },
  {
    id: 'mes',
    etiqueta: 'Este mes',
    rango: () => {
      const hoy = new Date();
      return { desde: iso(new Date(hoy.getFullYear(), hoy.getMonth(), 1)), hasta: iso(hoy) };
    },
  },
  {
    id: 'anio',
    etiqueta: 'Este año',
    rango: () => {
      const hoy = new Date();
      return { desde: iso(new Date(hoy.getFullYear(), 0, 1)), hasta: iso(hoy) };
    },
  },
];

/** Atajos de rango de fechas; resalta el atajo que coincide con el rango actual. */
@Component({
  selector: 'ui-rango-rapido',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ui-rango" role="group" aria-label="Rangos rápidos de fecha">
      @for (o of opciones; track o.id) {
        <button
          type="button"
          class="ui-rango__btn"
          [class.ui-rango__btn--on]="activo() === o.id"
          [attr.aria-pressed]="activo() === o.id"
          (click)="rango.emit(o.rango())"
        >
          {{ o.etiqueta }}
        </button>
      }
    </div>
  `,
})
export class RangoRapido {
  readonly desde = input('');
  readonly hasta = input('');
  readonly rango = output<RangoFechas>();

  readonly opciones = OPCIONES;

  readonly activo = computed(() => {
    const desde = this.desde();
    const hasta = this.hasta();
    if (!desde || !hasta) {
      return null;
    }
    return OPCIONES.find((o) => {
      const r = o.rango();
      return r.desde === desde && r.hasta === hasta;
    })?.id ?? null;
  });
}
