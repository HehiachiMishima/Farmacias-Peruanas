import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { mensajeDeError } from '../../../../../../core/errors/error.util';
import { PERMISOS } from '../../../../../../core/config/permisos';
import { Notificaciones } from '../../../../../../core/services/notificaciones';
import { SistemasApi } from '../../../../../../core/services/sistemas.api';
import { Sistema, TipoIncidencia } from '../../../../../../core/models';
import { HasPermiso } from '../../../../../../shared/directives/has-permiso.directive';
import { ColumnasRedimensionables } from '../../../../../../shared/directives/columnas-redimensionables.directive';
import { EmptyState } from '../../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../../shared/ui/paginador/paginador';
import { ActorLabelPipe } from '../../../../../../shared/pipes/actor-label.pipe';

@Component({
  selector: 'app-bitacora-sistemas',
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
  templateUrl: './sistemas.html',
  styleUrl: './sistemas.scss',
})
export class Sistemas {
  private readonly fb = inject(FormBuilder);
  private readonly sistemasApi = inject(SistemasApi);
  private readonly notificaciones = inject(Notificaciones);

  readonly permisos = PERMISOS;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly sistemasLista = signal<Sistema[]>([]);
  readonly busqueda = signal('');

  readonly filtrados = computed(() => {
    const termino = this.busqueda().trim().toLowerCase();
    const lista = this.sistemasLista();
    if (!termino) {
      return lista;
    }
    return lista.filter((sistema) => sistema.sistema.toLowerCase().includes(termino));
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
  readonly editando = signal<Sistema | null>(null);
  readonly guardando = signal(false);

  readonly formulario = this.fb.nonNullable.group({
    sistema: ['', [Validators.required, Validators.maxLength(50)]],
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<Sistema | null>(null);
  readonly eliminando = signal(false);

  readonly tiposAbierto = signal(false);
  readonly sistemaTipos = signal<Sistema | null>(null);
  readonly tiposLista = signal<TipoIncidencia[]>([]);
  readonly cargandoTipos = signal(false);

  readonly tipoFormAbierto = signal(false);
  readonly tipoEditando = signal<TipoIncidencia | null>(null);
  readonly tipoGuardando = signal(false);

  readonly tipoFormulario = this.fb.nonNullable.group({
    incidencia: ['', [Validators.required, Validators.maxLength(50)]],
  });

  readonly tipoEliminarAbierto = signal(false);
  readonly tipoAEliminar = signal<TipoIncidencia | null>(null);
  readonly tipoEliminando = signal(false);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.sistemasApi
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (lista) => this.sistemasLista.set(lista),
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
  }

  campoInvalido(campo: 'sistema'): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({ sistema: '' });
    this.formAbierto.set(true);
  }

  abrirEditar(sistema: Sistema): void {
    this.editando.set(sistema);
    this.formulario.reset({ sistema: sistema.sistema });
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
    const peticion = { sistema: valores.sistema };

    this.guardando.set(true);
    const peticion$ = editando
      ? this.sistemasApi.actualizar(editando.id, peticion)
      : this.sistemasApi.crear(peticion);

    peticion$.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (sistema) => {
        this.notificaciones.exito(
          'Guardado',
          `Sistema ${sistema.sistema} ${editando ? 'actualizado' : 'creado'}.`,
        );
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  confirmarEliminar(sistema: Sistema): void {
    this.aEliminar.set(sistema);
    this.eliminarAbierto.set(true);
  }

  eliminar(): void {
    const sistema = this.aEliminar();
    if (!sistema || this.eliminando()) {
      return;
    }
    this.eliminando.set(true);
    this.sistemasApi
      .eliminar(sistema.id)
      .pipe(finalize(() => this.eliminando.set(false)))
      .subscribe({
        next: () => {
          this.sistemasLista.update((lista) => lista.filter((item) => item.id !== sistema.id));
          this.notificaciones.exito('Eliminado', `Sistema ${sistema.sistema} eliminado.`);
          this.eliminarAbierto.set(false);
          this.aEliminar.set(null);
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }

  abrirTipos(sistema: Sistema): void {
    this.sistemaTipos.set(sistema);
    this.tiposAbierto.set(true);
    this.tipoFormAbierto.set(false);
    this.tipoEditando.set(null);
    this.cargarTipos();
  }

  private cargarTipos(): void {
    const sistema = this.sistemaTipos();
    if (!sistema) {
      return;
    }
    this.cargandoTipos.set(true);
    this.sistemasApi
      .tiposDe(sistema.id)
      .pipe(finalize(() => this.cargandoTipos.set(false)))
      .subscribe({
        next: (lista) => this.tiposLista.set(lista),
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los tipos', mensajeDeError(err)),
      });
  }

  abrirTipoCrear(): void {
    this.tipoEditando.set(null);
    this.tipoFormulario.reset({ incidencia: '' });
    this.tipoFormAbierto.set(true);
  }

  abrirTipoEditar(tipo: TipoIncidencia): void {
    this.tipoEditando.set(tipo);
    this.tipoFormulario.reset({ incidencia: tipo.incidencia });
    this.tipoFormAbierto.set(true);
  }

  cerrarTipoForm(): void {
    this.tipoFormAbierto.set(false);
    this.tipoEditando.set(null);
  }

  campoTipoInvalido(): boolean {
    const control = this.tipoFormulario.controls.incidencia;
    return control.touched && control.invalid;
  }

  guardarTipo(): void {
    const sistema = this.sistemaTipos();
    if (!sistema || this.tipoGuardando()) {
      return;
    }
    if (this.tipoFormulario.invalid) {
      this.tipoFormulario.markAllAsTouched();
      return;
    }

    const valores = this.tipoFormulario.getRawValue();
    const editando = this.tipoEditando();
    const peticion = { incidencia: valores.incidencia };

    this.tipoGuardando.set(true);
    const peticion$ = editando
      ? this.sistemasApi.actualizarTipo(sistema.id, editando.id, peticion)
      : this.sistemasApi.crearTipo(sistema.id, peticion);

    peticion$.pipe(finalize(() => this.tipoGuardando.set(false))).subscribe({
      next: () => {
        this.notificaciones.exito(
          'Guardado',
          `Tipo de incidencia ${editando ? 'actualizado' : 'creado'}.`,
        );
        this.cerrarTipoForm();
        this.cargarTipos();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  confirmarEliminarTipo(tipo: TipoIncidencia): void {
    this.tipoAEliminar.set(tipo);
    this.tipoEliminarAbierto.set(true);
  }

  eliminarTipo(): void {
    const sistema = this.sistemaTipos();
    const tipo = this.tipoAEliminar();
    if (!sistema || !tipo || this.tipoEliminando()) {
      return;
    }
    this.tipoEliminando.set(true);
    this.sistemasApi
      .eliminarTipo(sistema.id, tipo.id)
      .pipe(finalize(() => this.tipoEliminando.set(false)))
      .subscribe({
        next: () => {
          this.tiposLista.update((lista) => lista.filter((item) => item.id !== tipo.id));
          this.notificaciones.exito('Eliminado', `Tipo de incidencia ${tipo.incidencia} eliminado.`);
          this.tipoEliminarAbierto.set(false);
          this.tipoAEliminar.set(null);
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }
}
