import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { mensajeDeError } from '../../../../../../core/errors/error.util';
import { PERMISOS } from '../../../../../../core/config/permisos';
import { Notificaciones } from '../../../../../../core/services/notificaciones';
import { BitacoraCatalogos } from '../../../../../../core/services/bitacora-catalogos';
import { DirectorioUsuarios } from '../../../../../../core/services/directorio-usuarios';
import { AreasApi } from '../../../../../../core/services/areas.api';
import { Area, Responsable } from '../../../../../../core/models';
import { HasPermiso } from '../../../../../../shared/directives/has-permiso.directive';
import { ColumnasRedimensionables } from '../../../../../../shared/directives/columnas-redimensionables.directive';
import { EmptyState } from '../../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../../shared/ui/paginador/paginador';
import { ActorLabelPipe } from '../../../../../../shared/pipes/actor-label.pipe';

@Component({
  selector: 'app-bitacora-areas',
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
  templateUrl: './areas.html',
  styleUrl: './areas.scss',
})
export class Areas {
  private readonly fb = inject(FormBuilder);
  private readonly areasApi = inject(AreasApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly catalogos = inject(BitacoraCatalogos);
  private readonly directorio = inject(DirectorioUsuarios);

  readonly permisos = PERMISOS;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly areasLista = signal<Area[]>([]);
  readonly busqueda = signal('');

  readonly filtrados = computed(() => {
    const termino = this.busqueda().trim().toLowerCase();
    const lista = this.areasLista();
    if (!termino) {
      return lista;
    }
    return lista.filter((area) => area.area.toLowerCase().includes(termino));
  });

  readonly pagina = signal(1);
  readonly porPagina = signal(10);

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.filtrados().length / this.porPagina())),
  );

  readonly paginaActual = computed(() =>
    Math.min(Math.max(1, this.pagina()), this.totalPaginas()),
  );

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
  readonly editando = signal<Area | null>(null);
  readonly guardando = signal(false);

  readonly formulario = this.fb.nonNullable.group({
    area: ['', [Validators.required, Validators.maxLength(50)]],
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<Area | null>(null);
  readonly eliminando = signal(false);

  readonly hijosAbierto = signal(false);
  readonly areaHijos = signal<Area | null>(null);
  readonly hijosLista = signal<Responsable[]>([]);
  readonly cargandoHijos = signal(false);

  readonly hijoFormAbierto = signal(false);
  readonly hijoEditando = signal<Responsable | null>(null);
  readonly hijoGuardando = signal(false);

  readonly hijoFormulario = this.fb.nonNullable.group({
    responsable: ['', [Validators.required, Validators.maxLength(50)]],
  });

  readonly hijoEliminarAbierto = signal(false);
  readonly hijoAEliminar = signal<Responsable | null>(null);
  readonly hijoEliminando = signal(false);

  constructor() {
    this.directorio.cargar();
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.areasApi
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (lista) => this.areasLista.set(lista),
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
  }

  campoInvalido(campo: 'area'): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({ area: '' });
    this.formAbierto.set(true);
  }

  abrirEditar(area: Area): void {
    this.editando.set(area);
    this.formulario.reset({ area: area.area });
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
    const peticion = { area: valores.area };

    this.guardando.set(true);
    const peticion$ = editando
      ? this.areasApi.actualizar(editando.id, peticion)
      : this.areasApi.crear(peticion);

    peticion$.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (area) => {
        this.catalogos.invalidar();
        this.notificaciones.exito(
          'Guardado',
          `Área ${area.area} ${editando ? 'actualizada' : 'creada'}.`,
        );
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  confirmarEliminar(area: Area): void {
    this.aEliminar.set(area);
    this.eliminarAbierto.set(true);
  }

  eliminar(): void {
    const area = this.aEliminar();
    if (!area || this.eliminando()) {
      return;
    }
    this.eliminando.set(true);
    this.areasApi
      .eliminar(area.id)
      .pipe(finalize(() => this.eliminando.set(false)))
      .subscribe({
        next: () => {
          this.areasLista.update((lista) => lista.filter((item) => item.id !== area.id));
          this.catalogos.invalidar();
          this.notificaciones.exito('Eliminado', `Área ${area.area} eliminada.`);
          this.eliminarAbierto.set(false);
          this.aEliminar.set(null);
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }

  abrirHijos(area: Area): void {
    this.areaHijos.set(area);
    this.hijosAbierto.set(true);
    this.hijoFormAbierto.set(false);
    this.hijoEditando.set(null);
    this.cargarHijos();
  }

  private cargarHijos(): void {
    const area = this.areaHijos();
    if (!area) {
      return;
    }
    this.cargandoHijos.set(true);
    this.areasApi
      .responsablesDe(area.id)
      .pipe(finalize(() => this.cargandoHijos.set(false)))
      .subscribe({
        next: (lista) => this.hijosLista.set(lista),
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los responsables', mensajeDeError(err)),
      });
  }

  abrirHijoCrear(): void {
    this.hijoEditando.set(null);
    this.hijoFormulario.reset({ responsable: '' });
    this.hijoFormAbierto.set(true);
  }

  abrirHijoEditar(responsable: Responsable): void {
    this.hijoEditando.set(responsable);
    this.hijoFormulario.reset({ responsable: responsable.responsable });
    this.hijoFormAbierto.set(true);
  }

  cerrarHijoForm(): void {
    this.hijoFormAbierto.set(false);
    this.hijoEditando.set(null);
  }

  campoHijoInvalido(): boolean {
    const control = this.hijoFormulario.controls.responsable;
    return control.touched && control.invalid;
  }

  guardarHijo(): void {
    const area = this.areaHijos();
    if (!area || this.hijoGuardando()) {
      return;
    }
    if (this.hijoFormulario.invalid) {
      this.hijoFormulario.markAllAsTouched();
      return;
    }

    const valores = this.hijoFormulario.getRawValue();
    const editando = this.hijoEditando();
    const peticion = { responsable: valores.responsable };

    this.hijoGuardando.set(true);
    const peticion$ = editando
      ? this.areasApi.actualizarResponsable(area.id, editando.id, peticion)
      : this.areasApi.crearResponsable(area.id, peticion);

    peticion$.pipe(finalize(() => this.hijoGuardando.set(false))).subscribe({
      next: () => {
        this.catalogos.invalidar();
        this.notificaciones.exito('Guardado', `Responsable ${editando ? 'actualizado' : 'creado'}.`);
        this.cerrarHijoForm();
        this.cargarHijos();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  confirmarEliminarHijo(responsable: Responsable): void {
    this.hijoAEliminar.set(responsable);
    this.hijoEliminarAbierto.set(true);
  }

  eliminarHijo(): void {
    const area = this.areaHijos();
    const responsable = this.hijoAEliminar();
    if (!area || !responsable || this.hijoEliminando()) {
      return;
    }
    this.hijoEliminando.set(true);
    this.areasApi
      .eliminarResponsable(area.id, responsable.id)
      .pipe(finalize(() => this.hijoEliminando.set(false)))
      .subscribe({
        next: () => {
          this.hijosLista.update((lista) => lista.filter((item) => item.id !== responsable.id));
          this.catalogos.invalidar();
          this.notificaciones.exito('Eliminado', `Responsable ${responsable.responsable} eliminado.`);
          this.hijoEliminarAbierto.set(false);
          this.hijoAEliminar.set(null);
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }
}
