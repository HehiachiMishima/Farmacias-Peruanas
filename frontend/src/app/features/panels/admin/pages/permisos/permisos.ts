import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { PERMISOS } from '../../../../../core/config/permisos';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { DirectorioUsuarios } from '../../../../../core/services/directorio-usuarios';
import { PermisosApi } from '../../data/permisos.api';
import { Permiso } from '../../data/permiso.model';
import { Rol } from '../../data/rol.model';
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { ColumnasRedimensionables } from '../../../../../shared/directives/columnas-redimensionables.directive';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../shared/ui/paginador/paginador';
import { ChipFiltro, FiltrosActivos } from '../../../../../shared/ui/panel-filtros/filtros-activos';
import {
  FiltroEstado,
  OPCIONES_ESTADO,
  chipEstado,
  chipPeriodo,
  cumpleEstado,
  enPeriodo,
} from '../../../../../shared/ui/panel-filtros/filtro-registro';
import { contieneTexto } from '../../../../../core/utils/texto.util';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';
import { RangoFechas, RangoRapido } from '../../../../../shared/ui/panel-filtros/rango-rapido';
import { ActorLabelPipe } from '../../../../../shared/pipes/actor-label.pipe';

@Component({
  selector: 'app-admin-permisos',
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
    PanelFiltros,
    FiltrosActivos,
    RangoRapido,
  ],
  templateUrl: './permisos.html',
  styleUrl: './permisos.scss',
})
export class Permisos {
  private readonly fb = inject(FormBuilder);
  private readonly permisosApi = inject(PermisosApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly directorio = inject(DirectorioUsuarios);

  readonly permisos = PERMISOS;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly permisosLista = signal<Permiso[]>([]);
  readonly busqueda = signal('');

  private readonly coincidenBusqueda = computed(() => {
    const termino = this.busqueda();
    return this.permisosLista().filter((permiso) =>
      contieneTexto([permiso.nombre, permiso.descripcion], termino),
    );
  });

  // ---------------- Filtros del panel ----------------
  readonly filtroEstado = signal<FiltroEstado>('todos');
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');
  readonly filtrosAbierto = signal(false);
  readonly estados = OPCIONES_ESTADO;

  /** Búsqueda + filtros del panel (todo en cliente: el listado ya está en memoria). */
  readonly filtrados = computed(() => {
    const estado = this.filtroEstado();
    const desde = this.filtroDesde();
    const hasta = this.filtroHasta();
    return this.coincidenBusqueda().filter(
      (item) => cumpleEstado(estado, item.activo) && enPeriodo(item.creadoEn, desde, hasta),
    );
  });

  readonly chips = computed<ChipFiltro[]>(() =>
    [chipEstado(this.filtroEstado()), chipPeriodo(this.filtroDesde(), this.filtroHasta())].filter(
      (chip): chip is ChipFiltro => chip !== null,
    ),
  );

  readonly filtrosActivos = computed(() => this.chips().length);

  alternarFiltros(): void {
    this.filtrosAbierto.update((abierto) => !abierto);
  }

  setEstado(estado: FiltroEstado): void {
    this.filtroEstado.set(estado);
    this.pagina.set(1);
  }

  setRango(rango: RangoFechas): void {
    this.filtroDesde.set(rango.desde);
    this.filtroHasta.set(rango.hasta);
    this.pagina.set(1);
  }

  setFiltroDesde(evento: Event): void {
    this.filtroDesde.set((evento.target as HTMLInputElement).value);
    this.pagina.set(1);
  }

  setFiltroHasta(evento: Event): void {
    this.filtroHasta.set((evento.target as HTMLInputElement).value);
    this.pagina.set(1);
  }

  quitarFiltro(clave: string): void {
    if (clave === 'estado') this.filtroEstado.set('todos');
    if (clave === 'periodo') {
      this.filtroDesde.set('');
      this.filtroHasta.set('');
    }
    this.pagina.set(1);
  }

  limpiarFiltros(): void {
    this.filtroEstado.set('todos');
    this.filtroDesde.set('');
    this.filtroHasta.set('');
    this.pagina.set(1);
  }

  readonly pagina = signal(1);
  readonly porPagina = signal(10);

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.filtrados().length / this.porPagina())),
  );

  readonly paginaActual = computed(() => Math.min(Math.max(1, this.pagina()), this.totalPaginas()));

  readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.porPagina();
    return this.filtrados().slice(inicio, inicio + this.porPagina());
  });

  buscar(valor: string): void {
    this.busqueda.set(valor);
    this.pagina.set(1);
  }

  cambiarPorPagina(valor: number): void {
    this.porPagina.set(valor);
    this.pagina.set(1);
  }

  readonly formAbierto = signal(false);
  readonly editando = signal<Permiso | null>(null);
  readonly guardando = signal(false);

  readonly formulario = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: [''],
    activo: [true],
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<Permiso | null>(null);
  readonly eliminando = signal(false);

  readonly rolesAbierto = signal(false);
  readonly permisoRoles = signal<Permiso | null>(null);
  readonly cargandoRoles = signal(false);
  readonly rolesConPermiso = signal<Rol[]>([]);

  constructor() {
    this.directorio.cargar();
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.permisosApi
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (lista) => this.permisosLista.set(lista),
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
  }

  campoInvalido(campo: 'nombre'): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({ nombre: '', descripcion: '', activo: true });
    this.formAbierto.set(true);
  }

  abrirEditar(permiso: Permiso): void {
    this.editando.set(permiso);
    this.formulario.reset({
      nombre: permiso.nombre,
      descripcion: permiso.descripcion ?? '',
      activo: permiso.activo,
    });
    this.formAbierto.set(true);
  }

  cerrarForm(): void {
    this.formAbierto.set(false);
    this.editando.set(null);
  }

  guardar(): void {
    if (this.guardando()) {
      return;
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    const valores = this.formulario.getRawValue();
    const editando = this.editando();
    const peticion = {
      nombre: valores.nombre,
      descripcion: valores.descripcion || null,
      activo: valores.activo,
    };

    this.guardando.set(true);
    const peticion$ = editando
      ? this.permisosApi.actualizar(editando.id, peticion)
      : this.permisosApi.crear(peticion);

    peticion$.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (permiso) => {
        this.notificaciones.exito(
          'Guardado',
          `Permiso ${permiso.nombre} ${editando ? 'actualizado' : 'creado'}.`,
        );
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  toggleActivo(permiso: Permiso): void {
    this.permisosApi.cambiarActivo(permiso.id, !permiso.activo).subscribe({
      next: (actualizado) => {
        this.permisosLista.update((lista) =>
          lista.map((item) => (item.id === actualizado.id ? actualizado : item)),
        );
        this.notificaciones.info(
          actualizado.activo ? 'Permiso activado' : 'Permiso desactivado',
          actualizado.nombre,
        );
      },
      error: (err: unknown) =>
        this.notificaciones.error('No se pudo actualizar', mensajeDeError(err)),
    });
  }

  confirmarEliminar(permiso: Permiso): void {
    this.aEliminar.set(permiso);
    this.eliminarAbierto.set(true);
  }

  eliminar(): void {
    const permiso = this.aEliminar();
    if (!permiso || this.eliminando()) {
      return;
    }
    this.eliminando.set(true);
    this.permisosApi
      .eliminar(permiso.id)
      .pipe(finalize(() => this.eliminando.set(false)))
      .subscribe({
        next: () => {
          this.permisosLista.update((lista) => lista.filter((item) => item.id !== permiso.id));
          this.notificaciones.exito('Eliminado', `Permiso ${permiso.nombre} eliminado.`);
          this.eliminarAbierto.set(false);
          this.aEliminar.set(null);
        },
        error: (err: unknown) =>
          this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }

  abrirRoles(permiso: Permiso): void {
    this.permisoRoles.set(permiso);
    this.rolesAbierto.set(true);
    this.cargandoRoles.set(true);
    this.permisosApi
      .roles(permiso.id)
      .pipe(finalize(() => this.cargandoRoles.set(false)))
      .subscribe({
        next: (roles) => this.rolesConPermiso.set(roles),
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los roles', mensajeDeError(err)),
      });
  }
}
