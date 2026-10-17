import { Service, inject, signal } from '@angular/core';
import { Usuario } from '../models';
import { UsuariosApi } from './usuarios.api';

/**
 * Caché id → nombre de usuario para resolver las columnas creado_por / actualizado_por.
 * Se alimenta con la lista de /usuarios cuando la página ya la cargó, o la pide una sola vez.
 */
@Service()
export class DirectorioUsuarios {
  private readonly usuariosApi = inject(UsuariosApi);

  private readonly nombres = signal<Map<number, string>>(new Map());
  readonly mapa = this.nombres.asReadonly();

  private cargando = false;
  private cargado = false;

  /** Alimenta el directorio con una lista ya obtenida (evita una petición extra). */
  establecer(lista: Usuario[]): void {
    this.nombres.set(new Map(lista.map((usuario) => [usuario.id, usuario.usuario])));
    this.cargado = true;
  }

  /** Carga el directorio una sola vez. */
  cargar(): void {
    if (this.cargado || this.cargando) {
      return;
    }
    this.cargando = true;
    this.usuariosApi.listar().subscribe({
      next: (lista) => {
        this.establecer(lista);
        this.cargando = false;
      },
      error: () => {
        this.cargando = false;
      },
    });
  }

  nombre(id: number): string | null {
    return this.nombres().get(id) ?? null;
  }
}
