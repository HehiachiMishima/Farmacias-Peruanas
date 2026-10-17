import { Service, computed, signal } from '@angular/core';

@Service()
export class Carga {
  private readonly estado = signal<{ visible: boolean; texto: string }>({
    visible: false,
    texto: '',
  });

  readonly visible = computed(() => this.estado().visible);
  readonly texto = computed(() => this.estado().texto);

  mostrar(texto = 'Cargando…'): void {
    this.estado.set({ visible: true, texto });
  }

  ocultar(): void {
    this.estado.set({ visible: false, texto: '' });
  }
}
