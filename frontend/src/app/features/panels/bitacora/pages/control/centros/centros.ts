import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { mensajeDeError } from '../../../../../../core/errors/error.util';
import { PERMISOS } from '../../../../../../core/config/permisos';
import { Notificaciones } from '../../../../../../core/services/notificaciones';
import { BitacoraCatalogos } from '../../../../../../core/services/bitacora-catalogos';
import { DirectorioUsuarios } from '../../../../../../core/services/directorio-usuarios';
import { CentrosApi } from '../../../../../../core/services/centros.api';
import { Centro } from '../../../../../../core/models';
import { HasPermiso } from '../../../../../../shared/directives/has-permiso.directive';
import { ColumnasRedimensionables } from '../../../../../../shared/directives/columnas-redimensionables.directive';
import { EmptyState } from '../../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../../shared/ui/paginador/paginador';
import { ActorLabelPipe } from '../../../../../../shared/pipes/actor-label.pipe';

@Component({
  selector: 'app-bitacora-centros',
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
  templateUrl: './centros.html',
  styleUrl: './centros.scss',
})
export class Centros {
  private readonly fb = inject(FormBuilder);
  private readonly centrosApi = inject(CentrosApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly catalogos = inject(BitacoraCatalogos);
  private readonly directorio = inject(DirectorioUsuarios);

  readonly permisos = PERMISOS;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly centrosLista = signal<Centro[]>([]);
  readonly busqueda = signal('');

  readonly filtrados = computed(() => {
    const termino = this.busqueda().trim().toLowerCase();
    const lista = this.centrosLista();
    if (!termino) {
      return lista;
    }
    return lista.filter((centro) => centro.centro.toLowerCase().includes(termino));
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
  readonly editando = signal<Centro | null>(null);
  readonly guardando = signal(false);

  readonly formulario = this.fb.nonNullable.group({
    centro: ['', [Validators.required, Validators.maxLength(50)]],
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<Centro | null>(null);
  readonly eliminando = signal(false);

  constructor() {
    this.directorio.cargar();
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.centrosApi
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (lista) => this.centrosLista.set(lista),
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
  }

  campoInvalido(campo: 'centro'): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({ centro: '' });
    this.formAbierto.set(true);
  }

  abrirEditar(centro: Centro): void {
    this.editando.set(centro);
    this.formulario.reset({ centro: centro.centro });
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
    const peticion = { centro: valores.centro };

    this.guardando.set(true);
    const peticion$ = editando
      ? this.centrosApi.actualizar(editando.id, peticion)
      : this.centrosApi.crear(peticion);

    peticion$.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (centro) => {
        this.catalogos.invalidar();
        this.notificaciones.exito(
          'Guardado',
          `Centro ${centro.centro} ${editando ? 'actualizado' : 'creado'}.`,
        );
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  confirmarEliminar(centro: Centro): void {
    this.aEliminar.set(centro);
    this.eliminarAbierto.set(true);
  }

  eliminar(): void {
    const centro = this.aEliminar();
    if (!centro || this.eliminando()) {
      return;
    }
    this.eliminando.set(true);
    this.centrosApi
      .eliminar(centro.id)
      .pipe(finalize(() => this.eliminando.set(false)))
      .subscribe({
        next: () => {
          this.centrosLista.update((lista) => lista.filter((item) => item.id !== centro.id));
          this.catalogos.invalidar();
          this.notificaciones.exito('Eliminado', `Centro ${centro.centro} eliminado.`);
          this.eliminarAbierto.set(false);
          this.aEliminar.set(null);
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }
}
