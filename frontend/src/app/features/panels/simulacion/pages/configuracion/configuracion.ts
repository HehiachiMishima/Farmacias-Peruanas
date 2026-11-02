import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject, debounceTime, finalize, of, switchMap, tap } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import {
  ArchivoSimulacion,
  ConfiguracionSimulacion,
  ConfiguracionSimulacionRequest,
  HistorialSimulacion,
  RutaSimulacion,
} from '../../data/simulacion.model';
import { Pagina } from '../../../../../core/models';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { SesionStore } from '../../../../../core/services/sesion';
import { SimulacionApi } from '../../data/simulacion.api';
import { ListadoRemoto } from '../../../../../core/utils/listado-remoto';
import { DirectorioUsuarios } from '../../../../../core/services/directorio-usuarios';
import { ActorLabelPipe } from '../../../../../shared/pipes/actor-label.pipe';
import { ColumnasRedimensionables } from '../../../../../shared/directives/columnas-redimensionables.directive';
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../shared/ui/paginador/paginador';
import { ChipFiltro, FiltrosActivos } from '../../../../../shared/ui/panel-filtros/filtros-activos';
import { PanelFiltros } from '../../../../../shared/ui/panel-filtros/panel-filtros';
import { SelectBuscable } from '../../../../../shared/ui/select-buscable/select-buscable';
import { SelectMultiple } from '../../../../../shared/ui/select-multiple/select-multiple';
import {
  LIMITES,
  OPERARIOS_INICIALES,
  PALETA_REGISTROS,
  VELOCIDAD_INICIAL,
  colorCluster,
  compararNombre,
  datosCarga,
  etiquetaRegistro,
  formatearHoras,
  formatearNumero,
  horas,
  nombreRegistroSugerido,
  problemaArchivoExcel,
  tamanoArchivo,
} from '../../simulacion-formato';

@Component({
  selector: 'app-simulacion-configuracion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ActorLabelPipe,
    ReactiveFormsModule,
    HasPermiso,
    ColumnasRedimensionables,
    EmptyState,
    Icon,
    Modal,
    Paginador,
    SelectBuscable,
    SelectMultiple,
    PanelFiltros,
    FiltrosActivos,
  ],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.scss',
})
export class SimulacionConfiguracion {
  private readonly api = inject(SimulacionApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sesion = inject(SesionStore);
  private readonly fb = inject(FormBuilder);
  private readonly directorio = inject(DirectorioUsuarios);
  private readonly inputArchivo = viewChild<ElementRef<HTMLInputElement>>('inputArchivo');

  readonly permisos = PERMISOS;
  readonly paleta = PALETA_REGISTROS;
  readonly tamanoArchivo = tamanoArchivo;
  readonly puedeModificar = computed(() =>
    this.sesion.permisos().includes(PERMISOS.MODIFICAR_CONFIGURACION_SIMULACION),
  );
  readonly formatearHoras = formatearHoras;
  readonly formatearNumero = formatearNumero;

  readonly registros = signal<ArchivoSimulacion[]>([]);
  readonly seleccionado = signal<number | null>(null);
  readonly cargandoOpciones = signal(false);

  readonly config = signal<ConfiguracionSimulacion | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly errorOpciones = signal<string | null>(null);
  /** Descarta respuestas viejas si el usuario cambia de registro rápido. */
  private solicitud = 0;

  readonly limitesValidos = LIMITES;
  readonly velocidad = signal(VELOCIDAD_INICIAL);
  readonly operariosMaximos = signal(OPERARIOS_INICIALES);
  /** Máximo de operarios por chute; null = reparto parejo del máximo global. */
  readonly operariosPorChute = signal<number | null>(null);
  readonly limites = signal<Record<number, number>>({});
  readonly operarios = signal<Record<number, number>>({});
  readonly dirty = signal(false);

  /** Operarios que suman los chutes con los valores en edición. */
  readonly operariosAsignados = computed(() =>
    Object.values(this.operarios()).reduce((total, valor) => total + valor, 0),
  );

  /** El tope debe cubrir lo ya asignado; si no, no se puede guardar. */
  readonly excedeMaximo = computed(() => this.operariosAsignados() > this.operariosMaximos());

  /** Con el tope justo, el balanceo no puede añadir operarios (solo mover rutas o reasignar). */
  readonly sinCupo = computed(() => this.operariosAsignados() === this.operariosMaximos());

  /**
   * Tope por chute que aplicará el balanceo con los valores en edición: el
   * definido o, si está vacío, el máximo global repartido entre los chutes.
   */
  readonly topePorChute = computed(() => {
    const definido = this.operariosPorChute();
    if (definido !== null) {
      return definido;
    }
    const chutes = Math.max(1, this.config()?.chutes.length ?? 1);
    return Math.max(
      1,
      Math.min(LIMITES.operariosPorChute, Math.ceil(this.operariosMaximos() / chutes)),
    );
  });

  /**
   * Chutes que pasan el máximo por chute definido. Cuenta también los
   * operarios que dejaría «Restablecer» (la base del usuario), igual que el backend.
   */
  readonly chutesSobreTope = computed(() => {
    const tope = this.operariosPorChute();
    if (tope === null) {
      return [];
    }
    const operarios = this.operarios();
    return (this.config()?.chutes ?? [])
      .map((chute) => {
        const valor = operarios[chute.id] ?? chute.operarios;
        const base = valor !== chute.operarios ? valor : chute.operariosOriginal;
        return { nombre: chute.nombre, operarios: Math.max(valor, base) };
      })
      .filter((chute) => chute.operarios > tope);
  });

  readonly filtroChute = signal<number[]>([]);
  readonly filtroCluster = signal<number[]>([]);
  readonly filtroTexto = signal('');
  /** Lo que muestra el buscador; el filtro se aplica tras la pausa de escritura. */
  readonly textoBusqueda = signal('');
  readonly filtrosAbierto = signal(false);
  private readonly busqueda$ = new Subject<string>();

  readonly pagina = signal(1);
  readonly porPagina = signal(25);

  readonly guardando = signal(false);
  readonly balanceando = signal(false);
  readonly restableciendo = signal(false);
  readonly moviendo = signal(false);
  /** Recarga tras balancear o restablecer: hasta que llegue, el formulario sigue bloqueado. */
  readonly recargando = signal(false);
  /** Una sola escritura a la vez: los botones de acción se deshabilitan juntos. */
  readonly ocupado = computed(
    () =>
      this.guardando() ||
      this.balanceando() ||
      this.restableciendo() ||
      this.moviendo() ||
      this.recargando(),
  );

  readonly historial = signal<HistorialSimulacion[]>([]);
  readonly historialAbierto = signal(false);
  readonly historialCargando = signal(false);

  /**
   * Historial agrupado por ejecución: los cambios de un mismo balanceo
   * comparten fecha (el backend se la asigna junta); cada movimiento manual
   * va aparte. Llega del más reciente al más antiguo.
   */
  readonly historialAgrupado = computed(() => {
    const grupos: {
      clave: string;
      titulo: string;
      fecha: string;
      creadoPor: number | null;
      rutas: number;
      separaciones: number;
      reasignados: number;
      anadidos: number;
      cambios: HistorialSimulacion[];
    }[] = [];
    for (const cambio of this.historial()) {
      const manual = cambio.motivo === 'MANUAL';
      const ultimo = grupos[grupos.length - 1];
      const mismaEjecucion =
        !manual &&
        ultimo !== undefined &&
        ultimo.titulo === 'Balanceo' &&
        ultimo.fecha === cambio.creadoEn &&
        ultimo.creadoPor === cambio.creadoPor;
      const grupo = mismaEjecucion
        ? ultimo
        : {
            clave: `${cambio.creadoEn}-${cambio.id}`,
            titulo: manual ? 'Movimiento manual' : 'Balanceo',
            fecha: cambio.creadoEn,
            creadoPor: cambio.creadoPor,
            rutas: 0,
            separaciones: 0,
            reasignados: 0,
            anadidos: 0,
            cambios: [],
          };
      if (!mismaEjecucion) {
        grupos.push(grupo);
      }
      grupo.cambios.push(cambio);
      if (cambio.tipoAccion === 'MOVER_RUTA') {
        grupo.rutas++;
        if (cambio.motivo === 'SEPARACION') {
          grupo.separaciones++;
        }
      } else if (cambio.tipoAccion === 'MOVER_OPERARIO') {
        grupo.reasignados++;
      } else if (cambio.tipoAccion === 'AÑADIR_OPERARIO') {
        grupo.anadidos++;
      }
    }
    return grupos;
  });

  readonly restablecerAbierto = signal(false);

  // ---------------- Carga de Excel desde configuración ----------------
  readonly subirAbierto = signal(false);
  readonly archivoSeleccionado = signal<File | null>(null);
  readonly subiendo = signal(false);
  readonly explicacionAbierta = signal(false);
  readonly formulario = this.fb.nonNullable.group({
    nombreRegistro: [
      '',
      [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)],
    ],
    etiqueta: ['', [Validators.maxLength(50)]],
    colorHex: [PALETA_REGISTROS[0].hex],
  });

  readonly moverAbierto = signal(false);
  readonly rutaAMover = signal<RutaSimulacion | null>(null);
  readonly chuteDestino = signal<number | null>(null);

  /** Rutas paginadas en el servidor: un registro puede tener decenas de miles. */
  readonly rutas = new ListadoRemoto<RutaSimulacion>(
    () => this.fuenteRutas(),
    (ultima) => this.pagina.set(ultima),
  );

  readonly opcionesRegistro = computed(() =>
    this.registros().map((registro) => ({ id: registro.id, etiqueta: etiquetaRegistro(registro) })),
  );

  readonly opcionesChute = computed(() =>
    [...(this.config()?.chutes ?? [])]
      .sort((a, b) => compararNombre(a.nombre, b.nombre))
      .map((chute) => ({ id: chute.id, etiqueta: chute.nombre })),
  );

  readonly opcionesCluster = computed(() =>
    [...(this.config()?.clusters ?? [])]
      .sort((a, b) => compararNombre(a.nombre, b.nombre))
      .map((cluster) => ({ id: cluster.id, etiqueta: cluster.nombre })),
  );

  /**
   * Chutes a los que se puede mover la ruta: todos menos el actual y los que
   * ya tienen un cluster incompatible (CL1 y CL2 no comparten chute).
   */
  readonly destinosMover = computed(() => {
    const ruta = this.rutaAMover();
    const cfg = this.config();
    if (!ruta || !cfg) {
      return { opciones: [], excluidos: [] as string[] };
    }
    const incompatibles = new Set(
      cfg.clusters.find((cluster) => cluster.id === ruta.clusterId)?.incompatibles ?? [],
    );
    const bloqueados = new Set(
      cfg.chutes
        .filter((chute) => chute.clusterIds.some((id) => incompatibles.has(id)))
        .map((chute) => chute.id),
    );
    const opciones = this.opcionesChute().filter(
      (chute) => chute.id !== ruta.chuteId && !bloqueados.has(chute.id),
    );
    const excluidos = this.opcionesChute()
      .filter((chute) => chute.id !== ruta.chuteId && bloqueados.has(chute.id))
      .map((chute) => chute.etiqueta);
    return { opciones, excluidos };
  });

  readonly filtrosActivos = computed(
    () =>
      this.filtroChute().length + this.filtroCluster().length + (this.filtroTexto().trim() ? 1 : 0),
  );

  readonly chips = computed<ChipFiltro[]>(() => {
    const chips: ChipFiltro[] = [];
    for (const chute of this.opcionesChute()) {
      if (this.filtroChute().includes(Number(chute.id))) {
        chips.push({
          clave: `chute:${chute.id}`,
          etiqueta: 'Chute',
          valor: chute.etiqueta,
          icono: 'layers',
        });
      }
    }
    for (const cluster of this.opcionesCluster()) {
      if (this.filtroCluster().includes(Number(cluster.id))) {
        chips.push({
          clave: `cluster:${cluster.id}`,
          etiqueta: 'Cluster',
          valor: cluster.etiqueta,
          icono: 'area',
        });
      }
    }
    const texto = this.filtroTexto().trim();
    if (texto) {
      chips.push({ clave: 'texto:', etiqueta: 'Ruta', valor: texto, icono: 'search' });
    }
    return chips;
  });

  alternarFiltros(): void {
    this.filtrosAbierto.update((abierto) => !abierto);
  }

  readonly tieneLimites = computed(() =>
    Object.values(this.limites()).some((limite) => Number(limite) > 0),
  );

  /** Bultos por chute según la carga agregada del servidor. */
  private readonly bultosPorChute = computed(() => {
    const mapa = new Map<number, number>();
    for (const fila of this.config()?.carga ?? []) {
      mapa.set(fila.chuteId, (mapa.get(fila.chuteId) ?? 0) + Number(fila.bultos));
    }
    return mapa;
  });

  /**
   * Tiempo de cierre de cada cluster con los parámetros en edición (vista
   * previa antes de guardar): el máximo de sus chutes, y cada chute tarda
   * bultos / (velocidad × operarios).
   */
  readonly resumenClusters = computed(() => {
    const cfg = this.config();
    if (!cfg) {
      return [];
    }
    const operarios = this.operarios();
    const limites = this.limites();
    const velocidad = this.velocidad() || VELOCIDAD_INICIAL;
    const bultosPorChute = this.bultosPorChute();
    const indices = new Map(cfg.clusters.map((cluster, indice) => [cluster.id, indice]));

    const acumulado = new Map<
      number,
      {
        bultos: number;
        unidades: number;
        lineas: number;
        cod: number;
        rutas: number;
        chutes: Set<number>;
      }
    >();
    for (const fila of cfg.carga) {
      const actual = acumulado.get(fila.clusterId) ?? {
        bultos: 0,
        unidades: 0,
        lineas: 0,
        cod: 0,
        rutas: 0,
        chutes: new Set<number>(),
      };
      actual.bultos += Number(fila.bultos);
      actual.unidades += Number(fila.unidades);
      actual.lineas += Number(fila.lineas);
      actual.cod += Number(fila.cod);
      actual.rutas += Number(fila.rutas);
      actual.chutes.add(fila.chuteId);
      acumulado.set(fila.clusterId, actual);
    }

    return cfg.clusters.map((cluster) => {
      const datos = acumulado.get(cluster.id);
      let tiempo = 0;
      for (const chuteId of datos?.chutes ?? []) {
        tiempo = Math.max(
          tiempo,
          horas(
            bultosPorChute.get(chuteId) ?? 0,
            velocidad,
            operarios[chuteId] ?? OPERARIOS_INICIALES,
          ),
        );
      }
      const limite = limites[cluster.id] ?? 0;
      return {
        id: cluster.id,
        nombre: cluster.nombre,
        color: colorCluster(indices.get(cluster.id) ?? 0),
        limite,
        tiempo,
        excede: limite > 0 && tiempo > limite + 0.01,
        sinLimite: limite <= 0,
        bultos: datos?.bultos ?? 0,
        unidades: datos?.unidades ?? 0,
        lineas: datos?.lineas ?? 0,
        cod: datos?.cod ?? 0,
        rutas: datos?.rutas ?? 0,
      };
    });
  });

  constructor() {
    // Se compara con el filtro vigente (no con el último texto emitido): tras
    // «Limpiar», volver a escribir el mismo código sí vuelve a filtrar.
    this.busqueda$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((texto) => {
      if (texto.trim() === this.filtroTexto().trim()) {
        return;
      }
      this.filtroTexto.set(texto);
      this.recargarRutasDesde(1);
    });
    this.cargarOpciones();
  }

  /** Aviso del navegador al cerrar o recargar la pestaña con parámetros sin guardar. */
  @HostListener('window:beforeunload', ['$event'])
  alSalirDelNavegador(evento: BeforeUnloadEvent): void {
    if (this.dirty()) {
      evento.preventDefault();
    }
  }

  /** Lo consulta el guard de la ruta antes de navegar a otra pantalla. */
  puedeSalir(): boolean {
    return (
      !this.dirty() || window.confirm('Hay parámetros sin guardar. ¿Salir y descartar los cambios?')
    );
  }

  cargarOpciones(preferirId?: number): void {
    this.cargandoOpciones.set(true);
    this.errorOpciones.set(null);
    this.api
      .opciones()
      .pipe(
        finalize(() => this.cargandoOpciones.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (lista) => {
          this.registros.set(lista);
          if (lista.length === 0) {
            this.seleccionado.set(null);
            this.config.set(null);
            return;
          }
          // Refrescar la lista no debe reemplazar la configuración abierta si
          // hay parámetros sin guardar.
          if (this.dirty() && this.seleccionado() !== null) {
            return;
          }
          const solicitado = Number(this.route.snapshot.queryParamMap.get('registro'));
          const actual = this.seleccionado();
          const elegido =
            lista.find((registro) => registro.id === preferirId)?.id ??
            lista.find((registro) => registro.id === actual)?.id ??
            lista.find((registro) => registro.id === solicitado)?.id ??
            lista[0].id;
          this.seleccionar(elegido);
        },
        error: (err: unknown) => this.errorOpciones.set(mensajeDeError(err)),
      });
  }

  seleccionarDesdeSelector(id: number | null): void {
    if (this.ocupado()) {
      this.notificaciones.advertencia('Operación en curso', 'Espera a que termine la operación.');
      return;
    }
    if (this.dirty()) {
      this.notificaciones.advertencia(
        'Cambios sin guardar',
        'Guarda los parámetros antes de cambiar de registro.',
      );
      return;
    }
    this.seleccionar(id);
  }

  actualizar(): void {
    if (this.ocupado()) {
      return;
    }
    if (this.dirty()) {
      this.notificaciones.advertencia(
        'Cambios sin guardar',
        'Guarda los parámetros antes de actualizar los registros.',
      );
      return;
    }
    this.cargarOpciones();
  }

  seleccionar(id: number | null): void {
    // Los filtros pertenecen al registro: solo se limpian al cambiar de registro,
    // no al recargar el mismo con «Actualizar».
    if (id !== this.seleccionado()) {
      this.reiniciarFiltros();
      this.pagina.set(1);
      this.rutas.vaciar();
    }
    this.seleccionado.set(id);
    this.config.set(null);
    this.error.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { registro: id },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    if (id === null) {
      this.solicitud++;
      this.cargando.set(false);
      return;
    }
    this.cargarConfiguracion(id, true);
  }

  /**
   * Carga la configuración (parámetros + carga agregada) y la primera página
   * de rutas. Con `inicial` muestra el estado de carga a pantalla completa;
   * si no (tras balancear o restablecer), recarga en segundo plano con el
   * formulario bloqueado hasta que llegue, para no pisar lo que se edite.
   */
  private cargarConfiguracion(id: number, inicial = false): void {
    const solicitud = ++this.solicitud;
    if (inicial) {
      this.cargando.set(true);
    } else {
      this.recargando.set(true);
    }
    this.api
      .configuracion(id)
      .pipe(
        finalize(() => {
          if (solicitud === this.solicitud) {
            this.cargando.set(false);
            this.recargando.set(false);
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (config) => {
          if (solicitud === this.solicitud) {
            this.aplicarConfiguracion(config);
            this.rutas.recargar();
          }
        },
        error: (err: unknown) => {
          if (solicitud !== this.solicitud) {
            return;
          }
          if (inicial) {
            this.error.set(mensajeDeError(err));
          } else {
            this.notificaciones.error(
              'No se pudo actualizar la vista',
              `Los cambios se guardaron, pero la configuración no se recargó: ${mensajeDeError(err)} Pulsa «Actualizar».`,
            );
          }
        },
      });
  }

  private aplicarConfiguracion(config: ConfiguracionSimulacion): void {
    this.config.set(config);
    this.velocidad.set(Number(config.velocidadBase));
    this.operariosMaximos.set(Math.max(1, Number(config.operariosMaximos) || config.chutes.length));
    this.operariosPorChute.set(
      config.operariosPorChute === null ? null : Number(config.operariosPorChute),
    );
    this.limites.set(
      Object.fromEntries(
        config.clusters.map((cluster) => [cluster.id, Number(cluster.limiteHoras)]),
      ),
    );
    this.operarios.set(
      Object.fromEntries(config.chutes.map((chute) => [chute.id, chute.operarios])),
    );
    this.dirty.set(false);
  }

  private fuenteRutas(): Observable<Pagina<RutaSimulacion>> {
    const id = this.seleccionado();
    if (id === null) {
      return of({ contenido: [], total: 0, pagina: 1, tamano: this.porPagina(), totalPaginas: 0 });
    }
    return this.api.rutas(id, {
      pagina: this.pagina(),
      tamano: this.porPagina(),
      texto: this.filtroTexto().trim(),
      clusterIds: this.filtroCluster(),
      chuteIds: this.filtroChute(),
    });
  }

  getOperarios(chuteId: number): number {
    return this.operarios()[chuteId] ?? OPERARIOS_INICIALES;
  }

  campoInvalido(campo: 'nombreRegistro'): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  colorDeCluster(clusterId: number): string {
    const indice = (this.config()?.clusters ?? []).findIndex((cluster) => cluster.id === clusterId);
    return colorCluster(Math.max(0, indice));
  }

  /** Tiempo que aporta esta ruta al chute (no el total acumulado del chute). */
  tiempoRuta(ruta: RutaSimulacion): number {
    return horas(
      Number(ruta.totalBultos),
      this.velocidad() || VELOCIDAD_INICIAL,
      this.getOperarios(ruta.chuteId),
    );
  }

  setVelocidad(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const leido = Math.round(Number(input.value) * 100) / 100;
    if (!Number.isFinite(leido) || leido <= 0) {
      input.value = String(this.velocidad());
      return;
    }
    const valor = Math.min(LIMITES.velocidad, leido);
    input.value = String(valor);
    this.velocidad.set(valor);
    this.actualizarDirty();
  }

  setLimite(clusterId: number, evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const valor = Math.round(Number(input.value) * 100) / 100;
    const limite = Number.isFinite(valor) && valor >= 0 ? Math.min(LIMITES.horas, valor) : 0;
    input.value = String(limite);
    this.limites.set({ ...this.limites(), [clusterId]: limite });
    this.actualizarDirty();
  }

  setOperarios(chuteId: number, evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const valor = Number.parseInt(input.value, 10);
    const asignados =
      Number.isInteger(valor) && valor >= 1 ? Math.min(LIMITES.operariosPorChute, valor) : 1;
    input.value = String(asignados);
    this.operarios.set({ ...this.operarios(), [chuteId]: asignados });
    this.actualizarDirty();
  }

  setOperariosMaximos(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const valor = Number.parseInt(input.value, 10);
    const maximo =
      Number.isInteger(valor) && valor >= 1 ? Math.min(LIMITES.operariosTotal, valor) : 1;
    input.value = String(maximo);
    this.operariosMaximos.set(maximo);
    this.actualizarDirty();
  }

  /** Vacío = reparto parejo; si no, entre 1 y el tope técnico por chute. */
  setOperariosPorChute(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    if (input.value.trim() === '') {
      this.operariosPorChute.set(null);
      this.actualizarDirty();
      return;
    }
    const valor = Number.parseInt(input.value, 10);
    const tope =
      Number.isInteger(valor) && valor >= 1 ? Math.min(LIMITES.operariosPorChute, valor) : 1;
    input.value = String(tope);
    this.operariosPorChute.set(tope);
    this.actualizarDirty();
  }

  /**
   * Los cambios se marcan comparando con lo último cargado del servidor: editar
   * un valor y dejarlo igual no debe bloquear el cambio de registro ni la salida.
   */
  private actualizarDirty(): void {
    const config = this.config();
    if (!config) {
      this.dirty.set(false);
      return;
    }
    const limites = this.limites();
    const operarios = this.operarios();
    const iguales =
      Number(config.velocidadBase) === this.velocidad() &&
      Number(config.operariosMaximos) === this.operariosMaximos() &&
      (config.operariosPorChute ?? null) === this.operariosPorChute() &&
      config.clusters.every(
        (cluster) => Number(cluster.limiteHoras) === (limites[cluster.id] ?? 0),
      ) &&
      config.chutes.every(
        (chute) => chute.operarios === (operarios[chute.id] ?? OPERARIOS_INICIALES),
      );
    this.dirty.set(!iguales);
  }

  setFiltroChute(ids: (number | string)[]): void {
    this.filtroChute.set(ids.map(Number));
    this.recargarRutasDesde(1);
  }

  setFiltroCluster(ids: (number | string)[]): void {
    this.filtroCluster.set(ids.map(Number));
    this.recargarRutasDesde(1);
  }

  quitarFiltro(clave: string): void {
    const [tipo, valor] = clave.split(':');
    const id = Number(valor);
    if (tipo === 'chute') {
      this.setFiltroChute(this.filtroChute().filter((item) => item !== id));
    } else if (tipo === 'cluster') {
      this.setFiltroCluster(this.filtroCluster().filter((item) => item !== id));
    } else if (tipo === 'texto') {
      this.busqueda$.next('');
      this.textoBusqueda.set('');
      this.filtroTexto.set('');
      this.recargarRutasDesde(1);
    }
  }

  buscarRuta(texto: string): void {
    this.textoBusqueda.set(texto);
    this.busqueda$.next(texto);
  }

  limpiarFiltros(): void {
    this.reiniciarFiltros();
    this.recargarRutasDesde(1);
  }

  /** Vacía los filtros y descarta una búsqueda pendiente del debounce. */
  private reiniciarFiltros(): void {
    this.busqueda$.next('');
    this.textoBusqueda.set('');
    this.filtroChute.set([]);
    this.filtroCluster.set([]);
    this.filtroTexto.set('');
  }

  cambiarPagina(pagina: number): void {
    this.recargarRutasDesde(pagina);
  }

  cambiarPorPagina(valor: number): void {
    this.porPagina.set(valor);
    this.recargarRutasDesde(1);
  }

  private recargarRutasDesde(pagina: number): void {
    this.pagina.set(pagina);
    this.rutas.recargar();
  }

  private peticion(): ConfiguracionSimulacionRequest | null {
    const config = this.config();
    if (!config) {
      return null;
    }
    return {
      velocidadBase: this.velocidad(),
      operariosMaximos: this.operariosMaximos(),
      operariosPorChute: this.operariosPorChute(),
      clusters: config.clusters.map((cluster) => ({
        clusterId: cluster.id,
        limiteHoras: this.limites()[cluster.id] ?? 0,
      })),
      chutes: config.chutes.map((chute) => ({
        chuteId: chute.id,
        operarios: Math.max(1, this.getOperarios(chute.id)),
      })),
    };
  }

  /**
   * El máximo global no puede quedar por debajo de lo asignado, ni un chute
   * por encima del máximo por chute (el backend rechazaría el guardado).
   */
  private validarTope(): boolean {
    if (this.excedeMaximo()) {
      this.notificaciones.advertencia(
        'Operarios insuficientes',
        `Hay ${this.operariosAsignados()} operarios asignados y el máximo es ${this.operariosMaximos()}. Sube el máximo o reduce operarios en los chutes.`,
      );
      return false;
    }
    const sobreTope = this.chutesSobreTope();
    if (sobreTope.length > 0) {
      this.notificaciones.advertencia(
        'Máximo por chute',
        `${sobreTope.map((chute) => `${chute.nombre} (${chute.operarios})`).join(', ')} pasa(n) el máximo de ${this.operariosPorChute()} por chute. Ajusta esos chutes, sube el máximo o restablece el balanceo.`,
      );
      return false;
    }
    return true;
  }

  guardar(): void {
    const id = this.seleccionado();
    const peticion = this.peticion();
    if (!id || !peticion || this.ocupado()) {
      return;
    }
    if (!this.validarTope()) {
      return;
    }
    this.guardando.set(true);
    this.api
      .guardarConfiguracion(id, peticion)
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (config) => {
          if (this.seleccionado() !== id) {
            return;
          }
          this.aplicarConfiguracion(config);
          this.notificaciones.exito(
            'Parámetros guardados',
            'Los límites y operarios quedaron actualizados.',
          );
        },
      });
  }

  balancear(): void {
    const id = this.seleccionado();
    const peticion = this.peticion();
    if (!id || !peticion || this.ocupado()) {
      return;
    }
    if (!this.tieneLimites()) {
      this.notificaciones.advertencia(
        'Define los límites',
        'Ingresa al menos un límite de horas por cluster antes de balancear.',
      );
      return;
    }
    if (!this.validarTope()) {
      return;
    }
    this.balanceando.set(true);
    const operacion = this.dirty()
      ? this.guardarAntes(id, peticion).pipe(switchMap(() => this.api.balancear(id)))
      : this.api.balancear(id);
    operacion.pipe(finalize(() => this.balanceando.set(false))).subscribe({
      next: (resultado) => {
        this.notificaciones.exito('Balanceo ejecutado', resultado.mensaje);
        if (this.seleccionado() === id) {
          this.cargarConfiguracion(id);
        }
      },
    });
  }

  /**
   * Guarda los parámetros en edición antes de otra operación y aplica la
   * respuesta: si el segundo paso falla, lo guardado ya no figura como pendiente.
   */
  private guardarAntes(
    id: number,
    peticion: ConfiguracionSimulacionRequest,
  ): Observable<ConfiguracionSimulacion> {
    return this.api.guardarConfiguracion(id, peticion).pipe(
      tap((config) => {
        if (this.seleccionado() === id) {
          this.aplicarConfiguracion(config);
        }
      }),
    );
  }

  verCambios(): void {
    const id = this.seleccionado();
    if (!id) {
      return;
    }
    this.historial.set([]);
    this.directorio.cargar();
    this.historialAbierto.set(true);
    this.historialCargando.set(true);
    this.api
      .historial(id)
      .pipe(
        finalize(() => this.historialCargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (historial) => this.historial.set(historial),
        error: () => this.historialAbierto.set(false),
      });
  }

  confirmarRestablecer(): void {
    if (!this.ocupado()) {
      this.restablecerAbierto.set(true);
    }
  }

  restablecer(): void {
    const id = this.seleccionado();
    if (!id || this.ocupado()) {
      return;
    }
    this.restableciendo.set(true);
    this.api
      .restablecer(id)
      .pipe(finalize(() => this.restableciendo.set(false)))
      .subscribe({
        next: () => {
          this.restablecerAbierto.set(false);
          this.notificaciones.info(
            'Datos restablecidos',
            'Las rutas volvieron a su chute original y cada chute a los operarios que fijaste.',
          );
          if (this.seleccionado() === id) {
            this.cargarConfiguracion(id);
          }
        },
      });
  }

  abrirMover(ruta: RutaSimulacion): void {
    if (this.ocupado()) {
      return;
    }
    this.rutaAMover.set(ruta);
    this.chuteDestino.set(null);
    this.moverAbierto.set(true);
  }

  cerrarMover(): void {
    this.moverAbierto.set(false);
    this.rutaAMover.set(null);
    this.chuteDestino.set(null);
  }

  confirmarMover(): void {
    const id = this.seleccionado();
    const ruta = this.rutaAMover();
    const destino = this.chuteDestino();
    const peticion = this.peticion();
    if (!id || !ruta || destino === null || !peticion || this.ocupado()) {
      return;
    }
    this.moviendo.set(true);
    // Mover devuelve la configuración del servidor: si hay parámetros sin
    // guardar, se envían primero para no perderlos al aplicar la respuesta.
    const operacion = this.dirty()
      ? this.guardarAntes(id, peticion).pipe(
          switchMap(() => this.api.moverRuta(id, ruta.id, destino)),
        )
      : this.api.moverRuta(id, ruta.id, destino);
    operacion.pipe(finalize(() => this.moviendo.set(false))).subscribe({
      next: (config) => {
        if (this.seleccionado() === id) {
          this.aplicarConfiguracion(config);
          this.rutas.recargar();
        }
        this.cerrarMover();
        this.notificaciones.exito('Ruta movida', `La ruta ${ruta.codigoRuta} cambió de chute.`);
      },
    });
  }

  irADashboard(): void {
    const id = this.seleccionado();
    void this.router.navigate(['/simulacion/dashboard'], {
      queryParams: id ? { registro: id } : {},
    });
  }

  // ------------------------------------------------------------------
  // Carga de Excel desde la configuración
  // ------------------------------------------------------------------

  abrirSelectorArchivo(): void {
    if (this.ocupado()) {
      return;
    }
    if (this.dirty()) {
      this.notificaciones.advertencia(
        'Cambios sin guardar',
        'Guarda los parámetros antes de cargar otro Excel.',
      );
      return;
    }
    this.inputArchivo()?.nativeElement.click();
  }

  seleccionarArchivo(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo) {
      return;
    }
    const problema = problemaArchivoExcel(archivo);
    if (problema) {
      this.notificaciones.advertencia(problema.titulo, problema.mensaje);
      return;
    }
    this.archivoSeleccionado.set(archivo);
    this.formulario.reset({
      nombreRegistro: nombreRegistroSugerido(archivo),
      etiqueta: '',
      colorHex: PALETA_REGISTROS[0].hex,
    });
    this.subirAbierto.set(true);
  }

  cerrarCarga(): void {
    this.subirAbierto.set(false);
    this.archivoSeleccionado.set(null);
  }

  fijarColor(color: string): void {
    this.formulario.controls.colorHex.setValue(color);
  }

  confirmarCarga(): void {
    if (this.subiendo()) {
      return;
    }
    const archivo = this.archivoSeleccionado();
    if (!archivo) {
      return;
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.subiendo.set(true);
    this.api
      .subir(datosCarga(archivo, this.formulario.getRawValue()))
      .pipe(
        finalize(() => this.subiendo.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (registro) => {
          this.notificaciones.exito(
            'Registro creado',
            `${registro.rutas} rutas y ${registro.chutes} chutes cargados.`,
          );
          this.cerrarCarga();
          this.cargarOpciones(registro.id);
        },
        error: (err: unknown) => {
          // 503/504: la respuesta expiró pero el servidor pudo terminar de
          // guardar. Se refresca la lista para no duplicar el registro.
          if (err instanceof HttpErrorResponse && (err.status === 503 || err.status === 504)) {
            this.notificaciones.info(
              'Revisa la lista antes de reintentar',
              'La carga tardó más de lo esperado y pudo haberse completado.',
            );
            this.cerrarCarga();
            this.cargarOpciones();
          }
        },
      });
  }

  abrirExplicacion(): void {
    this.explicacionAbierta.set(true);
  }

  irARegistro(): void {
    void this.router.navigate(['/simulacion/registro']);
  }
}
