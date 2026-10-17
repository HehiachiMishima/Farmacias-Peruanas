import { AfterViewInit, Directive, ElementRef, OnDestroy, inject } from '@angular/core';

/**
 * Convierte una tabla en columnas de ancho fijo (el contenido se trunca con …)
 * y añade un asa en cada cabecera para redimensionar arrastrando.
 * Al arrastrar una columna se ajusta la vecina para mantener el ancho total.
 *
 * Los anchos se calculan después de pintarse las filas (los botones condicionados
 * con *hasPermiso se renderizan en un efecto, posterior a ngAfterViewInit).
 */
@Directive({ selector: 'table[appColumnasRedimensionables]' })
export class ColumnasRedimensionables implements AfterViewInit, OnDestroy {
  private readonly tabla = inject<ElementRef<HTMLTableElement>>(ElementRef);
  private readonly limpiezas: (() => void)[] = [];
  private temporizador: number | null = null;
  private marco: number | null = null;
  private marcoAcciones: number | null = null;

  ngAfterViewInit(): void {
    this.temporizador = window.setTimeout(() => {
      this.temporizador = null;
      this.marco = requestAnimationFrame(() => {
        this.marco = null;
        this.fijarAnchos();
      });
    }, 0);
  }

  private fijarAnchos(): void {
    const tabla = this.tabla.nativeElement;
    const fila = tabla.tHead?.rows[0];
    if (!fila) {
      return;
    }

    const celdas = Array.from(fila.cells) as HTMLTableCellElement[];
    if (celdas.length < 2) {
      return;
    }

    // Medir con el layout automático (ancho natural según el contenido).
    const anchos = celdas.map((celda) => Math.round(celda.getBoundingClientRect().width));
    const total = anchos.reduce((acumulado, ancho) => acumulado + ancho, 0);
    if (total <= 0) {
      return;
    }

    tabla.classList.add('ui-table--rigid');
    tabla.style.minWidth = `${total}px`;

    const colgroup = document.createElement('colgroup');
    anchos.forEach((ancho) => {
      const col = document.createElement('col');
      col.style.width = `${ancho}px`;
      colgroup.appendChild(col);
    });
    tabla.insertBefore(colgroup, tabla.firstChild);
    const cols = Array.from(colgroup.children) as HTMLTableColElement[];

    const aplicar = (indice: number): void => {
      cols[indice].style.width = `${anchos[indice]}px`;
    };

    celdas.forEach((celda, indice) => {
      const vecino = indice === celdas.length - 1 ? indice - 1 : indice + 1;
      if (vecino < 0) {
        return;
      }

      const asa = document.createElement('span');
      asa.className = 'col-resize';
      asa.setAttribute('aria-hidden', 'true');
      celda.appendChild(asa);

      const alBajar = (evento: PointerEvent): void => {
        evento.preventDefault();
        evento.stopPropagation();

        const xInicial = evento.clientX;
        const inicio = anchos[indice];
        const vecinoInicio = anchos[vecino];
        const par = inicio + vecinoInicio;
        const min = 72;
        const max = par - min;

        document.body.classList.add('col-resize--activo');

        const alMover = (mov: PointerEvent): void => {
          const nuevo = Math.min(Math.max(inicio + (mov.clientX - xInicial), min), max);
          anchos[indice] = nuevo;
          anchos[vecino] = par - nuevo;
          aplicar(indice);
          aplicar(vecino);
        };

        const alSoltar = (): void => {
          window.removeEventListener('pointermove', alMover);
          window.removeEventListener('pointerup', alSoltar);
          document.body.classList.remove('col-resize--activo');
        };

        window.addEventListener('pointermove', alMover);
        window.addEventListener('pointerup', alSoltar);
      };

      asa.addEventListener('pointerdown', alBajar);
      this.limpiezas.push(() => asa.removeEventListener('pointerdown', alBajar));
    });

    // Red de seguridad: si la última columna (acciones) quedó recortada,
    // ampliarla para que los botones siempre se vean.
    this.marcoAcciones = requestAnimationFrame(() => {
      this.marcoAcciones = null;
      const celda = tabla.tBodies[0]?.rows[0]?.cells[anchos.length - 1] as HTMLElement | undefined;
      if (!celda) {
        return;
      }
      const deficit = celda.scrollWidth - celda.clientWidth;
      if (deficit > 1) {
        const indice = anchos.length - 1;
        anchos[indice] += deficit;
        cols[indice].style.width = `${anchos[indice]}px`;
        tabla.style.minWidth = `${anchos.reduce((acumulado, ancho) => acumulado + ancho, 0)}px`;
      }
    });
  }

  ngOnDestroy(): void {
    if (this.temporizador !== null) {
      window.clearTimeout(this.temporizador);
    }
    if (this.marco !== null) {
      cancelAnimationFrame(this.marco);
    }
    if (this.marcoAcciones !== null) {
      cancelAnimationFrame(this.marcoAcciones);
    }
    this.limpiezas.forEach((limpiar) => limpiar());
    this.limpiezas.length = 0;
    document.body.classList.remove('col-resize--activo');
  }
}
