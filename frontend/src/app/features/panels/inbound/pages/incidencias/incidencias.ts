import { DatePipe, formatDate } from '@angular/common';
import { Component, LOCALE_ID, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, debounceTime, finalize } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { ListadoRemoto } from '../../../../../core/utils/listado-remoto';
import { exportarCsv } from '../../../../../core/utils/csv.util';
import {
  IncidenciaInbound,
  IncidenciaInboundCrearRequest,
  IncidenciaInboundFiltros,
} from '../../../../../core/models';
import { DirectorioUsuarios } from '../../../../../core/services/directorio-usuarios';
import { InboundCatalogos } from '../../../../../core/services/inbound-catalogos';
import { InboundIncidenciasApi } from '../../../../../core/services/inbound-incidencias.api';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { ColumnasRedimensionables } from '../../../../../shared/directives/columnas-redimensionables.directive';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../shared/ui/paginador/paginador';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';
import { SelectBuscable } from '../../../../../shared/ui/select-buscable/select-buscable';
import { ActorLabelPipe } from '../../../../../shared/pipes/actor-label.pipe';

type CampoFormulario =
  | 'ordenCompra'
  | 'idDataProveedor'
  | 'idDataIncidenciaInbound'
  | 'skuProducto'
  | 'nUnidades';

const CABECERAS_EXPORT = [
  'Orden de compra',
  'Proveedor',
  'Tipo',
  'SKU',
  'Unidades',
  'Creado',
  'Creado por',
  'Actualizado',
  'Actualizado por',
];

@Component({
  selector: 'app-inbound-incidencias',
  imports: [ReactiveFormsModule, DatePipe, ActorLabelPipe, Icon, Modal, EmptyState, HasPermiso, ColumnasRedimensionables, Paginador, PanelFiltros, SelectBuscable],
  templateUrl: './incidencias.html',
  styleUrl: './incidencias.scss',
})
export class InboundIncidencias {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(InboundIncidenciasApi);
  private readonly catalogos = inject(InboundCatalogos);
  private readonly notificaciones = inject(Notificaciones);
  private readonly directorio = inject(DirectorioUsuarios);
  private readonly locale = inject(LOCALE_ID);

  readonly permisos = PERMISOS;



  readonly proveedores = this.catalogos.proveedores;
  readonly tipos = this.catalogos.tipos;

  readonly opcionesProveedor = computed(() => this.proveedores().map((p) => ({ id: p.id, etiqueta: p.proveedor })));
  readonly opcionesTipo = computed(() => this.tipos().map((t) => ({ id: t.id, etiqueta: t.incidenciaInbound })));

  readonly filtroBusqueda = signal('');
  readonly filtroProveedor = signal<number | null>(null);
  readonly filtroTipo = signal<number | null>(null);
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');
  readonly filtrosAbierto = signal(false);

  private readonly busqueda$ = new Subject<string>();

  readonly filtrosActivos = computed(() => {
    let total = 0;
    if (this.filtroProveedor() !== null) total++;
    if (this.filtroTipo() !== null) total++;
    if (this.filtroDesde()) total++;
    if (this.filtroHasta()) total++;
    return total;
  });

  readonly hayFiltros = computed(() => this.filtrosActivos() > 0 || this.filtroBusqueda().trim() !== '');

  readonly pagina = signal(1);
  readonly porPagina = signal(10);

  private readonly listado = new ListadoRemoto<IncidenciaInbound>(
    () => this.api.listar(this.filtros(), this.pagina(), this.porPagina()),
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
  readonly editando = signal<IncidenciaInbound | null>(null);
  readonly guardando = signal(false);

  readonly formulario = this.fb.group({
    ordenCompra: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(30)]),
    idDataProveedor: this.fb.control<number | null>(null, Validators.required),
    idDataIncidenciaInbound: this.fb.control<number | null>(null, Validators.required),
    skuProducto: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(30)]),
    nUnidades: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<IncidenciaInbound | null>(null);
  readonly eliminando = signal(false);
  readonly exportando = signal(false);

  readonly verAbierto = signal(false);
  readonly aVer = signal<IncidenciaInbound | null>(null);

  constructor() {
    this.directorio.cargar();
    this.catalogos.cargar();
    this.cargar();

    this.busqueda$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((valor) => {
      if (valor.trim() === this.filtroBusqueda().trim()) {
        return;
      }
      this.filtroBusqueda.set(valor);
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

  private filtros(): IncidenciaInboundFiltros {
    return {
      proveedor: this.filtroProveedor(),
      tipo: this.filtroTipo(),
      desde: this.filtroDesde(),
      hasta: this.filtroHasta(),
      texto: this.filtroBusqueda(),
    };
  }

  exportar(): void {
    if (this.exportando() || this.total() === 0) return;
    this.exportando.set(true);
    this.api
      .exportar(this.filtros())
      .pipe(finalize(() => this.exportando.set(false)))
      .subscribe({
        next: (lista) => {
          const filas = lista.map((i) => [
            i.ordenCompra,
            i.proveedor ?? '—',
            i.tipoIncidencia ?? '—',
            i.skuProducto,
            i.nUnidades,
            formatDate(i.creadoEn, 'dd/MM/yyyy HH:mm', this.locale),
            this.directorio.etiqueta(i.creadoPor),
            i.actualizadoEn ? formatDate(i.actualizadoEn, 'dd/MM/yyyy HH:mm', this.locale) : '—',
            this.directorio.etiqueta(i.actualizadoPor),
          ]);
          exportarCsv(`incidencias_inbound_${this.hoy()}.csv`, CABECERAS_EXPORT, filas);
          this.notificaciones.exito('Exportado', `Se exportaron ${filas.length} incidencias.`);
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo exportar', mensajeDeError(err)),
      });
  }

  buscar(valor: string): void { this.busqueda$.next(valor); }

  alternarFiltros(): void { this.filtrosAbierto.update((a) => !a); }

  setFiltroProveedor(id: number | null): void {
    this.filtroProveedor.set(id);
    this.cargarDesde(1);
  }

  setFiltroTipo(id: number | null): void {
    this.filtroTipo.set(id);
    this.cargarDesde(1);
  }

  setFiltroDesde(evento: Event): void {
    this.filtroDesde.set((evento.target as HTMLInputElement).value);
    this.cargarDesde(1);
  }

  setFiltroHasta(evento: Event): void {
    this.filtroHasta.set((evento.target as HTMLInputElement).value);
    this.cargarDesde(1);
  }

  limpiarFiltros(): void {
    this.filtroBusqueda.set('');
    this.filtroProveedor.set(null);
    this.filtroTipo.set(null);
    this.filtroDesde.set('');
    this.filtroHasta.set('');
    this.cargarDesde(1);
  }

  cambiarPorPagina(valor: number): void { this.porPagina.set(valor); this.cargarDesde(1); }
  cambiarPagina(pagina: number): void { this.cargarDesde(pagina); }

  campoInvalido(campo: CampoFormulario): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({
      ordenCompra: '',
      idDataProveedor: null,
      idDataIncidenciaInbound: null,
      skuProducto: '',
      nUnidades: null,
    });
    this.formAbierto.set(true);
  }

  abrirEditar(i: IncidenciaInbound): void {
    this.editando.set(i);
    this.formulario.reset({
      ordenCompra: i.ordenCompra,
      idDataProveedor: i.idDataProveedor,
      idDataIncidenciaInbound: i.idDataIncidenciaInbound,
      skuProducto: i.skuProducto,
      nUnidades: i.nUnidades,
    });
    this.formAbierto.set(true);
  }

  cerrarForm(): void { this.formAbierto.set(false); this.editando.set(null); }

  guardar(): void {
    if (this.guardando()) return;
    if (this.formulario.invalid) { this.formulario.markAllAsTouched(); return; }
    const v = this.formulario.getRawValue();
    const peticion: IncidenciaInboundCrearRequest = {
      ordenCompra: v.ordenCompra,
      idDataProveedor: v.idDataProveedor!,
      idDataIncidenciaInbound: v.idDataIncidenciaInbound!,
      skuProducto: v.skuProducto,
      nUnidades: v.nUnidades!,
    };
    const editando = this.editando();
    this.guardando.set(true);
    const req$ = editando ? this.api.actualizar(editando.id, peticion) : this.api.crear(peticion);
    req$.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (i) => {
        this.notificaciones.exito('Guardado', `Incidencia ${i.ordenCompra} ${editando ? 'actualizada' : 'registrada'}.`);
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  confirmarEliminar(i: IncidenciaInbound): void { this.aEliminar.set(i); this.eliminarAbierto.set(true); }
  abrirVer(i: IncidenciaInbound): void { this.aVer.set(i); this.verAbierto.set(true); }
  cerrarVer(): void { this.verAbierto.set(false); this.aVer.set(null); }

  eliminar(): void {
    const i = this.aEliminar();
    if (!i || this.eliminando()) return;
    this.eliminando.set(true);
    this.api.eliminar(i.id).pipe(finalize(() => this.eliminando.set(false))).subscribe({
      next: () => {
        this.notificaciones.exito('Eliminado', `Incidencia ${i.ordenCompra} eliminada.`);
        this.eliminarAbierto.set(false);
        this.aEliminar.set(null);
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
    });
  }

  private hoy(): string {
    const ahora = new Date();
    const mes = `${ahora.getMonth() + 1}`.padStart(2, '0');
    const dia = `${ahora.getDate()}`.padStart(2, '0');
    return `${ahora.getFullYear()}-${mes}-${dia}`;
  }
}
