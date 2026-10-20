import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, debounceTime, finalize } from 'rxjs';
import { mensajeDeError } from '../../../../../../core/errors/error.util';
import { ListadoRemoto } from '../../../../../../core/utils/listado-remoto';
import { PERMISOS } from '../../../../../../core/config/permisos';
import { Notificaciones } from '../../../../../../core/services/notificaciones';
import { InboundCatalogos } from '../../../../../../core/services/inbound-catalogos';
import { DirectorioUsuarios } from '../../../../../../core/services/directorio-usuarios';
import { InboundControlApi } from '../../../../../../core/services/inbound-control.api';
import { TipoIncidenciaInbound, TipoIncidenciaInboundCrearRequest } from '../../../../../../core/models';
import { HasPermiso } from '../../../../../../shared/directives/has-permiso.directive';
import { ColumnasRedimensionables } from '../../../../../../shared/directives/columnas-redimensionables.directive';
import { EmptyState } from '../../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../../shared/ui/paginador/paginador';
import { ActorLabelPipe } from '../../../../../../shared/pipes/actor-label.pipe';

@Component({
  selector: 'app-tipos-inbound',
  imports: [ReactiveFormsModule, DatePipe, ActorLabelPipe, Icon, Modal, EmptyState, HasPermiso, ColumnasRedimensionables, Paginador],
  templateUrl: './tipos.html',
  styleUrl: './tipos.scss',
})
export class TiposInbound {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(InboundControlApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly catalogos = inject(InboundCatalogos);
  private readonly directorio = inject(DirectorioUsuarios);

  readonly permisos = PERMISOS;

  readonly busqueda = signal('');


  private readonly busqueda$ = new Subject<string>();

  readonly pagina = signal(1);
  readonly porPagina = signal(10);

  private readonly listado = new ListadoRemoto<TipoIncidenciaInbound>(
    () => this.api.listarTiposPaginado(this.pagina(), this.porPagina(), this.busqueda()),
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

  readonly formAbierto = signal(false);
  readonly editando = signal<TipoIncidenciaInbound | null>(null);
  readonly guardando = signal(false);
  readonly formulario = this.fb.nonNullable.group({
    incidenciaInbound: ['', [Validators.required, Validators.maxLength(100)]],
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<TipoIncidenciaInbound | null>(null);
  readonly eliminando = signal(false);

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

  buscar(valor: string): void { this.busqueda$.next(valor); }
  cambiarPorPagina(v: number): void { this.porPagina.set(v); this.cargarDesde(1); }
  cambiarPagina(pagina: number): void { this.cargarDesde(pagina); }

  campoInvalido(campo: 'incidenciaInbound'): boolean {
    const c = this.formulario.controls[campo];
    return c.touched && c.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({ incidenciaInbound: '' });
    this.formAbierto.set(true);
  }

  abrirEditar(x: TipoIncidenciaInbound): void {
    this.editando.set(x);
    this.formulario.reset({ incidenciaInbound: x.incidenciaInbound });
    this.formAbierto.set(true);
  }

  cerrarForm(): void { this.formAbierto.set(false); this.editando.set(null); }

  guardar(): void {
    if (this.guardando()) return;
    if (this.formulario.invalid) { this.formulario.markAllAsTouched(); return; }
    const v = this.formulario.getRawValue();
    const editando = this.editando();
    const peticion: TipoIncidenciaInboundCrearRequest = { incidenciaInbound: v.incidenciaInbound };
    this.guardando.set(true);
    const req$ = editando ? this.api.actualizarTipo(editando.id, peticion) : this.api.crearTipo(peticion);
    req$.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (x) => {
        this.catalogos.refrescar();
        this.notificaciones.exito('Guardado', `Tipo ${x.incidenciaInbound} ${editando ? 'actualizado' : 'creado'}.`);
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  confirmarEliminar(x: TipoIncidenciaInbound): void { this.aEliminar.set(x); this.eliminarAbierto.set(true); }
  eliminar(): void {
    const x = this.aEliminar();
    if (!x || this.eliminando()) return;
    this.eliminando.set(true);
    this.api.eliminarTipo(x.id).pipe(finalize(() => this.eliminando.set(false))).subscribe({
      next: () => {
        this.catalogos.refrescar();
        this.notificaciones.exito('Eliminado', `Tipo ${x.incidenciaInbound} eliminado.`);
        this.eliminarAbierto.set(false);
        this.aEliminar.set(null);
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
    });
  }
}
