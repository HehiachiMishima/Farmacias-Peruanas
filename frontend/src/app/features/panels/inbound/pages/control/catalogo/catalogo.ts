import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { PERMISOS } from '../../../../../../core/config/permisos';
import { DirectorioUsuarios } from '../../../../../../core/services/directorio-usuarios';
import { Notificaciones } from '../../../../../../core/services/notificaciones';
import { EdicionCrud } from '../../../../../../core/utils/edicion-crud';
import { ListadoRemoto } from '../../../../../../core/utils/listado-remoto';
import { ColumnasRedimensionables } from '../../../../../../shared/directives/columnas-redimensionables.directive';
import { HasPermiso } from '../../../../../../shared/directives/has-permiso.directive';
import { ActorLabelPipe } from '../../../../../../shared/pipes/actor-label.pipe';
import { EmptyState } from '../../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../../shared/ui/paginador/paginador';
import { InboundControlApi } from '../../../data/inbound-control.api';
import { InboundCatalogos } from '../../../state/inbound-catalogos';
import { CatalogoItem, DefCatalogo } from './catalogos';

/**
 * Página de Control Inbound para un catálogo de un solo campo (proveedores,
 * tipos de incidencia). El catálogo llega en `data.catalogo` de la ruta.
 */
@Component({
  selector: 'app-catalogo-inbound',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    ActorLabelPipe,
    Icon,
    Modal,
    EmptyState,
    HasPermiso,
    ColumnasRedimensionables,
    Paginador,
  ],
  templateUrl: './catalogo.html',
})
export class CatalogoInbound {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(InboundControlApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly catalogos = inject(InboundCatalogos);
  private readonly directorio = inject(DirectorioUsuarios);

  readonly catalogo: DefCatalogo = inject(ActivatedRoute).snapshot.data['catalogo'];
  readonly permisos = PERMISOS;

  readonly busqueda = signal('');
  private readonly busqueda$ = new Subject<string>();

  readonly pagina = signal(1);
  readonly porPagina = signal(10);

  private readonly listado = new ListadoRemoto<CatalogoItem>(
    () => this.catalogo.listar(this.api, this.pagina(), this.porPagina(), this.busqueda()),
    (ultima) => this.pagina.set(ultima),
  );
  readonly items = this.listado.items;
  readonly total = this.listado.total;
  readonly cargando = this.listado.cargando;
  readonly cargaInicial = this.listado.cargaInicial;
  readonly refrescando = this.listado.refrescando;
  readonly error = this.listado.error;

  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.total() / this.porPagina())));
  readonly paginaActual = computed(() => Math.min(Math.max(1, this.pagina()), this.totalPaginas()));

  readonly edicion = new EdicionCrud<CatalogoItem>();
  readonly formulario = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
  });

  constructor() {
    this.directorio.cargar();
    this.cargar();

    this.busqueda$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((valor) => {
      if (valor.trim() === this.busqueda().trim()) {
        return;
      }
      this.busqueda.set(valor);
      this.cargarDesde(1);
    });
  }

  cargar(): void {
    this.listado.recargar();
  }

  private cargarDesde(pagina: number): void {
    this.pagina.set(pagina);
    this.cargar();
  }

  buscar(valor: string): void {
    this.busqueda$.next(valor);
  }

  cambiarPorPagina(valor: number): void {
    this.porPagina.set(valor);
    this.cargarDesde(1);
  }

  cambiarPagina(pagina: number): void {
    this.cargarDesde(pagina);
  }

  campoInvalido(): boolean {
    const control = this.formulario.controls.nombre;
    return control.touched && control.invalid;
  }

  abrir(item: CatalogoItem | null): void {
    this.formulario.reset({ nombre: item ? this.catalogo.nombre(item) : '' });
    this.edicion.abrir(item);
  }

  guardar(): void {
    if (this.edicion.guardando()) {
      return;
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const { nombre } = this.formulario.getRawValue();
    const editando = this.edicion.editando();
    const peticion$ = editando
      ? this.catalogo.actualizar(this.api, editando.id, nombre)
      : this.catalogo.crear(this.api, nombre);
    this.edicion.guardar(peticion$, (item, editado) => {
      this.catalogos.invalidar();
      this.notificaciones.exito(
        'Guardado',
        `${this.catalogo.titulo} ${this.catalogo.nombre(item)} ${editado ? 'actualizado' : 'creado'}.`,
      );
      this.edicion.cerrar();
      this.cargar();
    });
  }

  eliminar(): void {
    this.edicion.eliminar(
      (item) => this.catalogo.eliminar(this.api, item.id),
      (item) => {
        this.catalogos.invalidar();
        this.notificaciones.exito(
          'Eliminado',
          `${this.catalogo.titulo} ${this.catalogo.nombre(item)} eliminado.`,
        );
        this.cargar();
      },
    );
  }
}
