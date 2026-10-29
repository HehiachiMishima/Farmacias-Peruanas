import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject, debounceTime, finalize, of, switchMap } from 'rxjs';
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
  OPERARIOS_INICIALES,
  VELOCIDAD_INICIAL,
  colorCluster,
  compararNombre,
  etiquetaRegistro,
  formatearHoras,
  formatearNumero,
  horas,
} from '../../simulacion-formato';

@Component({
  selector: 'app-simulacion-configuracion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
  private readonly sesion = inject(SesionStore);

  readonly permisos = PERMISOS;
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

  readonly velocidad = signal(VELOCIDAD_INICIAL);
  readonly limites = signal<Record<number, number>>({});
  readonly operarios = signal<Record<number, number>>({});
  readonly dirty = signal(false);

  readonly filtroChute = signal<number[]>([]);
  readonly filtroCluster = signal<number[]>([]);
  readonly filtroTexto = signal('');
  readonly filtrosAbierto = signal(false);
  private readonly busqueda$ = new Subject<string>();

  readonly pagina = signal(1);
  readonly porPagina = signal(25);

  readonly guardando = signal(false);
  readonly balanceando = signal(false);
  readonly restableciendo = signal(false);
  readonly moviendo = signal(false);
  /** Una sola escritura a la vez: los botones de acción se deshabilitan juntos. */
  readonly ocupado = computed(
    () => this.guardando() || this.balanceando() || this.restableciendo() || this.moviendo(),
  );

  readonly historial = signal<HistorialSimulacion[]>([]);
  readonly historialAbierto = signal(false);
  readonly historialCargando = signal(false);

  readonly restablecerAbierto = signal(false);

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

  readonly opcionesDestino = computed(() => {
    const ruta = this.rutaAMover();
    return this.opcionesChute().filter((chute) => chute.id !== ruta?.chuteId);
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

  cargarOpciones(): void {
    this.cargandoOpciones.set(true);
    this.errorOpciones.set(null);
    this.api
      .opciones()
      .pipe(finalize(() => this.cargandoOpciones.set(false)))
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
   * si no, recarga en segundo plano conservando la vista.
   */
  private cargarConfiguracion(id: number, inicial = false): void {
    const solicitud = ++this.solicitud;
    if (inicial) {
      this.cargando.set(true);
    }
    this.api
      .configuracion(id)
      .pipe(
        finalize(() => {
          if (solicitud === this.solicitud) {
            this.cargando.set(false);
          }
        }),
      )
      .subscribe({
        next: (config) => {
          if (solicitud === this.solicitud) {
            this.aplicarConfiguracion(config);
            this.rutas.recargar();
          }
        },
        error: (err: unknown) => {
          if (solicitud === this.solicitud && inicial) {
            this.error.set(mensajeDeError(err));
          }
        },
      });
  }

  private aplicarConfiguracion(config: ConfiguracionSimulacion): void {
    this.config.set(config);
    this.velocidad.set(Number(config.velocidadBase));
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
    const valor = Math.round(Number(input.value) * 100) / 100;
    if (!Number.isFinite(valor) || valor <= 0) {
      input.value = String(this.velocidad());
      return;
    }
    input.value = String(valor);
    this.velocidad.set(valor);
    this.actualizarDirty();
  }

  setLimite(clusterId: number, evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const valor = Math.round(Number(input.value) * 100) / 100;
    const limite = Number.isFinite(valor) && valor >= 0 ? valor : 0;
    input.value = String(limite);
    this.limites.set({ ...this.limites(), [clusterId]: limite });
    this.actualizarDirty();
  }

  setOperarios(chuteId: number, evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const valor = Number.parseInt(input.value, 10);
    const asignados = Number.isInteger(valor) && valor >= 1 ? valor : 1;
    input.value = String(asignados);
    this.operarios.set({ ...this.operarios(), [chuteId]: asignados });
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
      this.filtroTexto.set('');
      this.recargarRutasDesde(1);
    }
  }

  buscarRuta(texto: string): void {
    this.busqueda$.next(texto);
  }

  limpiarFiltros(): void {
    this.reiniciarFiltros();
    this.recargarRutasDesde(1);
  }

  /** Vacía los filtros y descarta una búsqueda pendiente del debounce. */
  private reiniciarFiltros(): void {
    this.busqueda$.next('');
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

  guardar(): void {
    const id = this.seleccionado();
    const peticion = this.peticion();
    if (!id || !peticion || this.ocupado()) {
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
    this.balanceando.set(true);
    const operacion = this.dirty()
      ? this.api.guardarConfiguracion(id, peticion).pipe(switchMap(() => this.api.balancear(id)))
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

  verCambios(): void {
    const id = this.seleccionado();
    if (!id) {
      return;
    }
    this.historial.set([]);
    this.historialAbierto.set(true);
    this.historialCargando.set(true);
    this.api
      .historial(id)
      .pipe(finalize(() => this.historialCargando.set(false)))
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
            'Las rutas volvieron a su posición original.',
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
      ? this.api
          .guardarConfiguracion(id, peticion)
          .pipe(switchMap(() => this.api.moverRuta(id, ruta.id, destino)))
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

  irARegistro(): void {
    void this.router.navigate(['/simulacion/registro']);
  }
}
