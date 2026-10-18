import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import {
  Area,
  Centro,
  Incidencia,
  IncidenciaCrearRequest,
  Responsable,
  Sistema,
  TipoIncidencia,
} from '../../../../../core/models';
import { AreasApi } from '../../../../../core/services/areas.api';
import { CentrosApi } from '../../../../../core/services/centros.api';
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

type CampoFormulario =
  | 'idDataCentro'
  | 'idDataSistema'
  | 'idDataIncidencia'
  | 'ticket'
  | 'idDataArea'
  | 'idDataResponsable'
  | 'descripcion'
  | 'horaInicio'
  | 'fechaCreacion';

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
  ],
  templateUrl: './incidencias.html',
  styleUrl: './incidencias.scss',
})
export class Incidencias {
  private readonly fb = inject(FormBuilder);
  private readonly incidenciasApi = inject(IncidenciasApi);
  private readonly centrosApi = inject(CentrosApi);
  private readonly sistemasApi = inject(SistemasApi);
  private readonly areasApi = inject(AreasApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly directorio = inject(DirectorioUsuarios);

  readonly permisos = PERMISOS;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly incidenciasLista = signal<Incidencia[]>([]);

  readonly centros = signal<Centro[]>([]);
  readonly sistemas = signal<Sistema[]>([]);
  readonly areas = signal<Area[]>([]);

  readonly filtroBusqueda = signal('');
  readonly filtroCentro = signal<number | null>(null);
  readonly filtroSistema = signal<number | null>(null);
  readonly filtroArea = signal<number | null>(null);
  readonly filtroResponsable = signal<number | null>(null);
  readonly filtroDesde = signal('');
  readonly filtroHasta = signal('');
  readonly responsablesFiltro = signal<Responsable[]>([]);
  readonly cargandoResponsablesFiltro = signal(false);

  readonly filtrados = computed(() => {
    const termino = this.filtroBusqueda().trim().toLowerCase();
    const centro = this.filtroCentro();
    const sistema = this.filtroSistema();
    const area = this.filtroArea();
    const responsable = this.filtroResponsable();
    const desde = this.filtroDesde();
    const hasta = this.filtroHasta();

    return this.incidenciasLista().filter((incidencia) => {
      if (
        termino &&
        !incidencia.ticket.toLowerCase().includes(termino) &&
        !incidencia.descripcion.toLowerCase().includes(termino)
      ) {
        return false;
      }
      if (centro !== null && incidencia.idDataCentro !== centro) {
        return false;
      }
      if (sistema !== null && incidencia.idDataSistema !== sistema) {
        return false;
      }
      if (area !== null && incidencia.idDataArea !== area) {
        return false;
      }
      if (responsable !== null && incidencia.idDataResponsable !== responsable) {
        return false;
      }
      const fecha = incidencia.creadoEn.slice(0, 10);
      if (desde && fecha < desde) {
        return false;
      }
      if (hasta && fecha > hasta) {
        return false;
      }
      return true;
    });
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
    horaFin: this.fb.nonNullable.control(''),
    fechaCreacion: this.fb.nonNullable.control('', Validators.required),
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<Incidencia | null>(null);
  readonly eliminando = signal(false);

  constructor() {
    this.directorio.cargar();
    this.cargarCatalogos();
    this.cargar();

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
    forkJoin({
      centros: this.centrosApi.listar(),
      sistemas: this.sistemasApi.listar(),
      areas: this.areasApi.listar(),
    }).subscribe({
      next: ({ centros, sistemas, areas }) => {
        this.centros.set(centros);
        this.sistemas.set(sistemas);
        this.areas.set(areas);
      },
      error: (err: unknown) =>
        this.notificaciones.error('No se pudieron cargar los catálogos', mensajeDeError(err)),
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.incidenciasApi
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (lista) => this.incidenciasLista.set(lista),
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
  }

  hhmm(valor: string | null): string {
    return valor ? valor.slice(0, 5) : '—';
  }

  buscar(valor: string): void {
    this.filtroBusqueda.set(valor);
    this.pagina.set(1);
  }

  setFiltroCentro(evento: Event): void {
    this.filtroCentro.set(this.valorNumero(evento));
    this.pagina.set(1);
  }

  setFiltroSistema(evento: Event): void {
    this.filtroSistema.set(this.valorNumero(evento));
    this.pagina.set(1);
  }

  setFiltroArea(evento: Event): void {
    const areaId = this.valorNumero(evento);
    this.filtroArea.set(areaId);
    this.filtroResponsable.set(null);
    this.responsablesFiltro.set([]);
    this.pagina.set(1);
    if (areaId !== null) {
      this.cargarResponsablesFiltro(areaId);
    }
  }

  setFiltroResponsable(evento: Event): void {
    this.filtroResponsable.set(this.valorNumero(evento));
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

  limpiarFiltros(): void {
    this.filtroBusqueda.set('');
    this.filtroCentro.set(null);
    this.filtroSistema.set(null);
    this.filtroArea.set(null);
    this.filtroResponsable.set(null);
    this.filtroDesde.set('');
    this.filtroHasta.set('');
    this.responsablesFiltro.set([]);
    this.pagina.set(1);
  }

  cambiarPorPagina(valor: number): void {
    this.porPagina.set(valor);
    this.pagina.set(1);
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
      horaFin: incidencia.horaFin ? this.hhmm(incidencia.horaFin) : '',
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
      horaFin: valores.horaFin ? this.normalizarHora(valores.horaFin) : null,
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
          this.incidenciasLista.update((lista) => lista.filter((item) => item.id !== incidencia.id));
          this.notificaciones.exito('Eliminado', `Incidencia ${incidencia.ticket} eliminada.`);
          this.eliminarAbierto.set(false);
          this.aEliminar.set(null);
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
