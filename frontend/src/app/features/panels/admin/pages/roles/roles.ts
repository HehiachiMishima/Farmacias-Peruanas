import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { DirectorioUsuarios } from '../../../../../core/services/directorio-usuarios';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { PermisosApi } from '../../data/permisos.api';
import { RolesApi } from '../../data/roles.api';
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
  selector: 'app-admin-roles',
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
  templateUrl: './roles.html',
  styleUrl: './roles.scss',
})
export class Roles {
  private readonly fb = inject(FormBuilder);
  private readonly rolesApi = inject(RolesApi);
  private readonly permisosApi = inject(PermisosApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly directorio = inject(DirectorioUsuarios);

  readonly permisos = PERMISOS;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly roles = signal<Rol[]>([]);
  readonly busqueda = signal('');

  private readonly coincidenBusqueda = computed(() => {
    const termino = this.busqueda();
    return this.roles().filter((rol) => contieneTexto([rol.nombre, rol.descripcion], termino));
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
  readonly editando = signal<Rol | null>(null);
  readonly guardando = signal(false);

  readonly formulario = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: [''],
    activo: [true],
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<Rol | null>(null);
  readonly eliminando = signal(false);

  readonly permisosAbierto = signal(false);
  readonly rolPermisos = signal<Rol | null>(null);
  readonly cargandoPermisos = signal(false);
  readonly catalogoPermisos = signal<Permiso[]>([]);
  readonly permisosAsignados = signal<Set<number>>(new Set());
  readonly cambiandoPermiso = signal<number | null>(null);

  constructor() {
    this.directorio.cargar();
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.rolesApi
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (lista) => this.roles.set(lista),
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
  }

  campoInvalido(campo: 'nombre' | 'descripcion'): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({ nombre: '', descripcion: '', activo: true });
    this.formAbierto.set(true);
  }

  abrirEditar(rol: Rol): void {
    this.editando.set(rol);
    this.formulario.reset({
      nombre: rol.nombre,
      descripcion: rol.descripcion ?? '',
      activo: rol.activo,
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
      ? this.rolesApi.actualizar(editando.id, peticion)
      : this.rolesApi.crear(peticion);

    peticion$.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (rol) => {
        this.notificaciones.exito(
          'Guardado',
          `Rol ${rol.nombre} ${editando ? 'actualizado' : 'creado'}.`,
        );
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  toggleActivo(rol: Rol): void {
    this.rolesApi.cambiarActivo(rol.id, !rol.activo).subscribe({
      next: (actualizado) => {
        this.roles.update((lista) =>
          lista.map((item) => (item.id === actualizado.id ? actualizado : item)),
        );
        this.notificaciones.info(
          actualizado.activo ? 'Rol activado' : 'Rol desactivado',
          actualizado.nombre,
        );
      },
      error: (err: unknown) =>
        this.notificaciones.error('No se pudo actualizar', mensajeDeError(err)),
    });
  }

  confirmarEliminar(rol: Rol): void {
    this.aEliminar.set(rol);
    this.eliminarAbierto.set(true);
  }

  eliminar(): void {
    const rol = this.aEliminar();
    if (!rol || this.eliminando()) {
      return;
    }
    this.eliminando.set(true);
    this.rolesApi
      .eliminar(rol.id)
      .pipe(finalize(() => this.eliminando.set(false)))
      .subscribe({
        next: () => {
          this.roles.update((lista) => lista.filter((item) => item.id !== rol.id));
          this.notificaciones.exito('Eliminado', `Rol ${rol.nombre} eliminado.`);
          this.eliminarAbierto.set(false);
          this.aEliminar.set(null);
        },
        error: (err: unknown) =>
          this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }

  abrirPermisos(rol: Rol): void {
    this.rolPermisos.set(rol);
    this.permisosAbierto.set(true);
    this.cargandoPermisos.set(true);
    forkJoin({
      catalogo: this.permisosApi.listar(),
      asignados: this.rolesApi.permisos(rol.id),
    })
      .pipe(finalize(() => this.cargandoPermisos.set(false)))
      .subscribe({
        next: ({ catalogo, asignados }) => {
          this.catalogoPermisos.set(catalogo);
          this.permisosAsignados.set(new Set(asignados.map((permiso) => permiso.id)));
        },
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los permisos', mensajeDeError(err)),
      });
  }

  estaAsignado(permisoId: number): boolean {
    return this.permisosAsignados().has(permisoId);
  }

  alternarPermiso(permiso: Permiso): void {
    const rol = this.rolPermisos();
    if (!rol || this.cambiandoPermiso()) {
      return;
    }
    this.cambiandoPermiso.set(permiso.id);
    const asignado = this.estaAsignado(permiso.id);
    const peticion$ = asignado
      ? this.rolesApi.quitarPermiso(rol.id, permiso.id)
      : this.rolesApi.asignarPermiso(rol.id, permiso.id);

    peticion$.pipe(finalize(() => this.cambiandoPermiso.set(null))).subscribe({
      next: () => {
        this.permisosAsignados.update((set) => {
          const copia = new Set(set);
          if (asignado) {
            copia.delete(permiso.id);
          } else {
            copia.add(permiso.id);
          }
          return copia;
        });
        this.notificaciones.info(
          asignado ? 'Permiso quitado del rol' : 'Permiso asignado al rol',
          permiso.nombre,
        );
      },
      error: (err: unknown) =>
        this.notificaciones.error('No se pudo actualizar', mensajeDeError(err)),
    });
  }
}
