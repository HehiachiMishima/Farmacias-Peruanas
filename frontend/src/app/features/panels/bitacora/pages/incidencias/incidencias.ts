import { DatePipe, formatDate } from '@angular/common';
import { Component, LOCALE_ID, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, debounceTime, finalize } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { exportarCsv } from '../../../../../core/utils/csv.util';
import { ListadoRemoto } from '../../../../../core/utils/listado-remoto';
import {
  Incidencia,
  IncidenciaCrearRequest,
  IncidenciaFiltros,
  Responsable,
  TipoIncidencia,
} from '../../../../../core/models';
import { AreasApi } from '../../../../../core/services/areas.api';
import { BitacoraCatalogos } from '../../../../../core/services/bitacora-catalogos';
import { DirectorioUsuarios } from '../../../../../core/services/directorio-usuarios';
import { IncidenciasApi } from '../../../../../core/services/incidencias.api';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { SistemasApi } from '../../../../../core/services/sistemas.api';
import { ActorLabelPipe } from '../../../../../shared/pipes/actor-label.pipe';
import { ColumnasRedimensionables } from '../../../../../shared/directives/columnas-redimensionables.directive';
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../shared/ui/paginador/paginador';
import { ChipFiltro, FiltrosActivos, fechaChip } from '../../../../../shared/ui/panel-filtros/filtros-activos';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';
import { RangoFechas, RangoRapido } from '../../../../../shared/ui/panel-filtros/rango-rapido';

type CampoFormulario =
  | 'idDataCentro'
  | 'idDataSistema'
  | 'idDataIncidencia'
  | 'ticket'
  | 'idDataArea'
  | 'idDataResponsable'
  | 'descripcion'
  | 'horaInicio'
  | 'horaFin'
  | 'fechaCreacion';

const CABECERAS_EXPORT = [
  'Ticket',
  'Descripción',
  'Centro',
  'Sistema',
  'Tipo',
  'Área',
  'Responsable',
  'Hora inicio',
  'Hora fin',
  'Tiempo de solución',
  'Creado',
  'Creado por',
  'Actualizado',
  'Actualizado por',
];

@Component({
  selector: 'app-bitacora-incidencias',
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
  templateUrl: './incidencias.html',
  styleUrl: './incidencias.scss',
})
export class Incidencias {
  private readonly fb = inject(FormBuilder);
  private readonly incidenciasApi = inject(IncidenciasApi);
  private readonly catalogos = inject(BitacoraCatalogos);
  private readonly sistemasApi = inject(SistemasApi);
  private readonly areasApi = inject(AreasApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly directorio = inject(DirectorioUsuarios);
  private readonly locale = inject(LOCALE_ID);

  readonly permisos = PERMISOS;

  readonly pagina = signal(1);
  readonly porPagina = signal(10);

  private readonly listado = new ListadoRemoto<Incidencia>(
    () => this.incidenciasApi.listar(this.filtros(), this.pagina(), this.porPagina()),
    (ultima) => this.pagina.set(ultima),
  );
  readonly items = this.listado.items;
  readonly total = this.listado.total;
  readonly cargando = this.listado.cargando;
  readonly cargaInicial = this.listado.cargaInicial;
  readonly refrescando = this.listado.refrescando;
  readonly error = this.listado.error;

  readonly centros = this.catalogos.centros;
  readonly sistemas = this.catalogos.sistemas;
  readonly areas = this.catalogos.areas;

  readonly filtroBusqueda = signal('');
  readonly filtroCentro = signal<number | null>(null);
  readonly filtroSistema = signal<number | null>(null);
  readonly filtroArea = signal<number | null>(null);
  readonly filtroResponsable = signal<number | null>(null);
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');
  readonly responsablesFiltro = signal<Responsable[]>([]);
  readonly cargandoResponsablesFiltro = signal(false);
  readonly filtrosAbierto = signal(false);

  private readonly busqueda$ = new Subject<string>();

  /** Filtros del panel aplicados, como chips removibles. */
  readonly chips = computed<ChipFiltro[]>(() => {
    const chips: ChipFiltro[] = [];
    const nombre = <T extends { id: number }>(lista: T[], id: number, campo: keyof T): string =>
      String(lista.find((x) => x.id === id)?.[campo] ?? `#${id}`);
    const centro = this.filtroCentro();
    if (centro !== null) chips.push({ clave: 'centro', etiqueta: 'Centro', valor: nombre(this.centros(), centro, 'centro'), icono: 'centro' });
    const sistema = this.filtroSistema();
    if (sistema !== null) chips.push({ clave: 'sistema', etiqueta: 'Sistema', valor: nombre(this.sistemas(), sistema, 'sistema'), icono: 'sistema' });
    const area = this.filtroArea();
    if (area !== null) chips.push({ clave: 'area', etiqueta: 'Área', valor: nombre(this.areas(), area, 'area'), icono: 'area' });
    const responsable = this.filtroResponsable();
    if (responsable !== null) chips.push({ clave: 'responsable', etiqueta: 'Responsable', valor: nombre(this.responsablesFiltro(), responsable, 'responsable'), icono: 'responsable' });
    if (this.filtroDesde()) chips.push({ clave: 'desde', etiqueta: 'Desde', valor: fechaChip(this.filtroDesde()), icono: 'calendario' });
    if (this.filtroHasta()) chips.push({ clave: 'hasta', etiqueta: 'Hasta', valor: fechaChip(this.filtroHasta()), icono: 'calendario' });
    return chips;
  });

  readonly filtrosActivos = computed(() => {
    let total = 0;
    if (this.filtroCentro() !== null) total++;
    if (this.filtroSistema() !== null) total++;
    if (this.filtroArea() !== null) total++;
    if (this.filtroResponsable() !== null) total++;
    if (this.filtroDesde()) total++;
    if (this.filtroHasta()) total++;
    return total;
  });

  readonly hayFiltros = computed(() => this.filtrosActivos() > 0 || this.filtroBusqueda().trim() !== '');

  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.total() / this.porPagina())));
  readonly paginaActual = computed(() => Math.min(Math.max(1, this.pagina()), this.totalPaginas()));

  readonly formAbierto = signal(false);
  readonly editando = signal<Incidencia | null>(null);
  readonly guardando = signal(false);
  readonly formTipos = signal<TipoIncidencia[]>([]);
  readonly formResponsables = signal<Responsable[]>([]);
  readonly cargandoFormTipos = signal(false);
  readonly cargandoFormResponsables = signal(false);

  private tipoPendiente: number | null = null;
  private responsablePendiente: number | null = null;

  readonly formulario = this.fb.group({
    idDataCentro: this.fb.control<number | null>(null, Validators.required),
    idDataSistema: this.fb.control<number | null>(null, Validators.required),
    idDataIncidencia: this.fb.control<number | null>(null, Validators.required),
    ticket: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(59)]),
    idDataArea: this.fb.control<number | null>(null, Validators.required),
    idDataResponsable: this.fb.control<number | null>(null, Validators.required),
    descripcion: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(255)]),
    horaInicio: this.fb.nonNullable.control('', Validators.required),
    horaFin: this.fb.nonNullable.control('', Validators.required),
    fechaCreacion: this.fb.nonNullable.control('', Validators.required),
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<Incidencia | null>(null);
  readonly eliminando = signal(false);

  readonly verAbierto = signal(false);
  readonly aVer = signal<Incidencia | null>(null);
  readonly exportando = signal(false);

  constructor() {
    this.directorio.cargar();
    this.cargarCatalogos();
    this.cargar();

    this.busqueda$
      .pipe(debounceTime(300), takeUntilDestroyed())
      .subscribe((valor) => {
        // Se compara contra el filtro vigente (no contra la última emisión):
        // así «Limpiar filtros» + volver a escribir lo mismo sí recarga.
        if (valor.trim() === this.filtroBusqueda().trim()) {
          return;
        }
        this.filtroBusqueda.set(valor);
        this.cargarDesde(1);
      });

    this.formulario.controls.idDataSistema.valueChanges.subscribe((sistemaId) => {
      this.cargarFormTipos(sistemaId, this.tipoPendiente);
      this.tipoPendiente = null;
    });
    this.formulario.controls.idDataArea.valueChanges.subscribe((areaId) => {
      this.cargarFormResponsables(areaId, this.responsablePendiente);
      this.responsablePendiente = null;
    });
  }

  cargarCatalogos(): void {
    this.catalogos.cargar();
  }

  cargar(): void {
    this.listado.recargar();
  }

  private cargarDesde(pagina: number): void {
    this.pagina.set(pagina);
    this.cargar();
  }

  private filtros(): IncidenciaFiltros {
    return {
      centro: this.filtroCentro(),
      sistema: this.filtroSistema(),
      area: this.filtroArea(),
      responsable: this.filtroResponsable(),
      desde: this.filtroDesde(),
      hasta: this.filtroHasta(),
      texto: this.filtroBusqueda(),
    };
  }

  hhmm(valor: string | null): string {
    return valor ? valor.slice(0, 5) : '—';
  }

  exportar(): void {
    if (this.exportando() || this.total() === 0) {
      return;
    }
    this.exportando.set(true);
    this.incidenciasApi
      .exportar(this.filtros())
      .pipe(finalize(() => this.exportando.set(false)))
      .subscribe({
        next: (lista) => {
          const filas = lista.map((incidencia) => [
            incidencia.ticket,
            incidencia.descripcion,
            incidencia.centro ?? '—',
            incidencia.sistema ?? '—',
            incidencia.tipoIncidencia ?? '—',
            incidencia.area ?? '—',
            incidencia.responsable ?? '—',
            incidencia.horaInicio,
            incidencia.horaFin,
            incidencia.tiempoSolucion ?? '—',
            formatDate(incidencia.creadoEn, 'dd/MM/yyyy HH:mm', this.locale),
            this.directorio.etiqueta(incidencia.creadoPor),
            incidencia.actualizadoEn
              ? formatDate(incidencia.actualizadoEn, 'dd/MM/yyyy HH:mm', this.locale)
              : '—',
            this.directorio.etiqueta(incidencia.actualizadoPor),
          ]);
          exportarCsv(`incidencias_${this.hoy()}.csv`, CABECERAS_EXPORT, filas);
          this.notificaciones.exito(
            'Exportado',
            `Se exportaron ${filas.length} ${filas.length === 1 ? 'incidencia' : 'incidencias'} a Excel (CSV).`,
          );
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo exportar', mensajeDeError(err)),
      });
  }

  buscar(valor: string): void {
    this.busqueda$.next(valor);
  }

  alternarFiltros(): void {
    this.filtrosAbierto.update((abierto) => !abierto);
  }

  setFiltroCentro(evento: Event): void {
    this.filtroCentro.set(this.valorNumero(evento));
    this.cargarDesde(1);
  }

  setFiltroSistema(evento: Event): void {
    this.filtroSistema.set(this.valorNumero(evento));
    this.cargarDesde(1);
  }

  setFiltroArea(evento: Event): void {
    const areaId = this.valorNumero(evento);
    this.filtroArea.set(areaId);
    this.filtroResponsable.set(null);
    this.responsablesFiltro.set([]);
    if (areaId !== null) {
      this.cargarResponsablesFiltro(areaId);
    }
    this.cargarDesde(1);
  }

  setFiltroResponsable(evento: Event): void {
    this.filtroResponsable.set(this.valorNumero(evento));
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

  quitarFiltro(clave: string): void {
    if (clave === 'centro') this.filtroCentro.set(null);
    if (clave === 'sistema') this.filtroSistema.set(null);
    if (clave === 'area') {
      this.filtroArea.set(null);
      this.filtroResponsable.set(null);
      this.responsablesFiltro.set([]);
    }
    if (clave === 'responsable') this.filtroResponsable.set(null);
    if (clave === 'desde') this.filtroDesde.set('');
    if (clave === 'hasta') this.filtroHasta.set('');
    this.cargarDesde(1);
  }

  setRango(rango: RangoFechas): void {
    this.filtroDesde.set(rango.desde);
    this.filtroHasta.set(rango.hasta);
    this.cargarDesde(1);
  }

  limpiarFiltros(): void {
    this.filtroBusqueda.set('');
    this.filtroCentro.set(null);
    this.filtroSistema.set(null);
    this.filtroArea.set(null);
    this.filtroResponsable.set(null);
    this.filtroDesde.set('');
    this.filtroHasta.set('');
    this.responsablesFiltro.set([]);
    this.cargarDesde(1);
  }

  cambiarPorPagina(valor: number): void {
    this.porPagina.set(valor);
    this.cargarDesde(1);
  }

  cambiarPagina(pagina: number): void {
    this.cargarDesde(pagina);
  }

  campoInvalido(campo: CampoFormulario): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.tipoPendiente = null;
    this.responsablePendiente = null;
    this.formTipos.set([]);
    this.formResponsables.set([]);
    this.formulario.reset({
      idDataCentro: null,
      idDataSistema: null,
      idDataIncidencia: null,
      ticket: '',
      idDataArea: null,
      idDataResponsable: null,
      descripcion: '',
      horaInicio: '',
      horaFin: '',
      fechaCreacion: this.hoy(),
    });
    this.formAbierto.set(true);
  }

  abrirEditar(incidencia: Incidencia): void {
    this.editando.set(incidencia);
    this.tipoPendiente = incidencia.idDataIncidencia;
    this.responsablePendiente = incidencia.idDataResponsable;
    this.formTipos.set([]);
    this.formResponsables.set([]);
    this.formulario.reset({
      idDataCentro: incidencia.idDataCentro,
      idDataSistema: incidencia.idDataSistema,
      idDataIncidencia: incidencia.idDataIncidencia,
      ticket: incidencia.ticket,
      idDataArea: incidencia.idDataArea,
      idDataResponsable: incidencia.idDataResponsable,
      descripcion: incidencia.descripcion,
      horaInicio: this.hhmm(incidencia.horaInicio),
      horaFin: this.hhmm(incidencia.horaFin),
      fechaCreacion: incidencia.creadoEn.slice(0, 10),
    });
    this.formAbierto.set(true);
  }

  cerrarForm(): void {
    this.formAbierto.set(false);
    this.editando.set(null);
    this.tipoPendiente = null;
    this.responsablePendiente = null;
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
    const peticion: IncidenciaCrearRequest = {
      idDataCentro: valores.idDataCentro!,
      idDataSistema: valores.idDataSistema!,
      idDataIncidencia: valores.idDataIncidencia!,
      ticket: valores.ticket,
      idDataArea: valores.idDataArea!,
      idDataResponsable: valores.idDataResponsable!,
      descripcion: valores.descripcion,
      horaInicio: this.normalizarHora(valores.horaInicio),
      horaFin: this.normalizarHora(valores.horaFin),
      fechaCreacion: valores.fechaCreacion,
    };

    const editando = this.editando();
    this.guardando.set(true);
    const peticion$ = editando
      ? this.incidenciasApi.actualizar(editando.id, peticion)
      : this.incidenciasApi.crear(peticion);

    peticion$.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (incidencia) => {
        this.notificaciones.exito(
          'Guardado',
          `Incidencia ${incidencia.ticket} ${editando ? 'actualizada' : 'registrada'}.`,
        );
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  confirmarEliminar(incidencia: Incidencia): void {
    this.aEliminar.set(incidencia);
    this.eliminarAbierto.set(true);
  }

  abrirVer(incidencia: Incidencia): void {
    this.aVer.set(incidencia);
    this.verAbierto.set(true);
  }

  cerrarVer(): void {
    this.verAbierto.set(false);
    this.aVer.set(null);
  }

  eliminar(): void {
    const incidencia = this.aEliminar();
    if (!incidencia || this.eliminando()) {
      return;
    }
    this.eliminando.set(true);
    this.incidenciasApi
      .eliminar(incidencia.id)
      .pipe(finalize(() => this.eliminando.set(false)))
      .subscribe({
        next: () => {
          this.notificaciones.exito('Eliminado', `Incidencia ${incidencia.ticket} eliminada.`);
          this.eliminarAbierto.set(false);
          this.aEliminar.set(null);
          this.cargar();
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }

  private cargarFormTipos(sistemaId: number | null, seleccion: number | null): void {
    this.formTipos.set([]);
    this.formulario.controls.idDataIncidencia.setValue(null, { emitEvent: false });
    if (sistemaId === null) {
      return;
    }
    this.cargandoFormTipos.set(true);
    this.sistemasApi
      .tiposDe(sistemaId)
      .pipe(finalize(() => this.cargandoFormTipos.set(false)))
      .subscribe({
        next: (lista) => {
          this.formTipos.set(lista);
          if (seleccion !== null) {
            this.formulario.controls.idDataIncidencia.setValue(seleccion, { emitEvent: false });
          }
        },
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los tipos', mensajeDeError(err)),
      });
  }

  private cargarFormResponsables(areaId: number | null, seleccion: number | null): void {
    this.formResponsables.set([]);
    this.formulario.controls.idDataResponsable.setValue(null, { emitEvent: false });
    if (areaId === null) {
      return;
    }
    this.cargandoFormResponsables.set(true);
    this.areasApi
      .responsablesDe(areaId)
      .pipe(finalize(() => this.cargandoFormResponsables.set(false)))
      .subscribe({
        next: (lista) => {
          this.formResponsables.set(lista);
          if (seleccion !== null) {
            this.formulario.controls.idDataResponsable.setValue(seleccion, { emitEvent: false });
          }
        },
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los responsables', mensajeDeError(err)),
      });
  }

  private cargarResponsablesFiltro(areaId: number): void {
    this.cargandoResponsablesFiltro.set(true);
    this.areasApi
      .responsablesDe(areaId)
      .pipe(finalize(() => this.cargandoResponsablesFiltro.set(false)))
      .subscribe({
        next: (lista) => this.responsablesFiltro.set(lista),
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los responsables', mensajeDeError(err)),
      });
  }

  private valorNumero(evento: Event): number | null {
    const valor = (evento.target as HTMLSelectElement).value;
    return valor ? Number(valor) : null;
  }

  private normalizarHora(valor: string): string {
    return valor.length === 5 ? `${valor}:00` : valor;
  }

  private hoy(): string {
    const ahora = new Date();
    const mes = `${ahora.getMonth() + 1}`.padStart(2, '0');
    const dia = `${ahora.getDate()}`.padStart(2, '0');
    return `${ahora.getFullYear()}-${mes}-${dia}`;
  }
}
