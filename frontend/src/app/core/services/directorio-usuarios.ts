import { Service, inject, signal } from '@angular/core';
import { PERMISOS } from '../config/permisos';
import { Usuario } from '../models';
import { SesionStore } from './sesion';
import { UsuariosApi } from './usuarios.api';

/**
 * Caché id → nombre de usuario para resolver las columnas creado_por / actualizado_por.
 * Se alimenta con la lista de /usuarios cuando la página ya la cargó, o la pide una sola vez.
 */
@Service()
export class DirectorioUsuarios {
  private readonly usuariosApi = inject(UsuariosApi);
  private readonly sesion = inject(SesionStore);

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
    // El catálogo de usuarios exige VER_USUARIOS: sin él la petición daría
    // 403 y un toast de error en pantallas que solo necesitan el fallback.
    if (!this.sesion.tienePermiso(PERMISOS.VER_USUARIOS)) {
      this.cargado = true;
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

  /**
   * Etiqueta legible para las columnas creado_por / actualizado_por.
   * 0 = sistema/seeding; null = sin dato; sin coincidencia = «Usuario #id».
   */
  etiqueta(valor: number | null | undefined): string {
    if (valor === null || valor === undefined) {
      return '—';
    }
    if (valor === 0) {
      return 'Sistema';
    }
    const nombre = this.nombre(valor);
    if (!nombre) {
      return `Usuario #${valor}`;
    }
    return nombre.startsWith('@') ? nombre : `@${nombre}`;
  }
}
