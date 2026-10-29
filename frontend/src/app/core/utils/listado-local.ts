import { Signal, computed, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { RecursoRemoto } from './listado-remoto';

/**
 * Catálogo pequeño cargado completo en memoria, con búsqueda y paginación en
 * cliente (centros, sistemas, áreas). La carga usa RecursoRemoto: cancela la
 * petición anterior, no muere ante un error y conserva las filas al recargar.
 *
 * Debe crearse en un contexto de inyección (campo o constructor de componente).
 */
export class ListadoLocal<T extends { id: number }> {
  private readonly recurso: RecursoRemoto<T[]>;

  readonly cargando: Signal<boolean>;
  readonly error: Signal<string | null>;
  readonly busqueda = signal('');
  readonly pagina = signal(1);
  readonly porPagina = signal(10);

  readonly filtrados = computed(() => {
    const termino = this.busqueda().trim().toLowerCase();
    const lista = this.recurso.valor() ?? [];
    if (!termino) {
      return lista;
    }
    return lista.filter((item) => this.texto(item).toLowerCase().includes(termino));
  });

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.filtrados().length / this.porPagina())),
  );

  readonly paginaActual = computed(() => Math.min(Math.max(1, this.pagina()), this.totalPaginas()));

  readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.porPagina();
    return this.filtrados().slice(inicio, inicio + this.porPagina());
  });

  constructor(
    fuente: () => Observable<T[]>,
    /** Texto del elemento sobre el que se busca. */
    private readonly texto: (item: T) => string,
  ) {
    this.recurso = new RecursoRemoto(fuente);
    this.cargando = this.recurso.cargando;
    this.error = this.recurso.error;
  }

  recargar(): void {
    this.recurso.recargar();
  }

  buscar(valor: string): void {
    this.busqueda.set(valor);
    this.pagina.set(1);
  }

  cambiarPorPagina(valor: number): void {
    this.porPagina.set(valor);
    this.pagina.set(1);
  }

  /** Quita un elemento eliminado sin volver a pedir la lista. */
  quitar(id: number): void {
    this.recurso.valor.update((lista) => lista?.filter((item) => item.id !== id) ?? null);
  }
}
