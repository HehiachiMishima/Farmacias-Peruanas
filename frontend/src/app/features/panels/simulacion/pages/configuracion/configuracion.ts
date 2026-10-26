import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { switchMap, finalize } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import {
  ArchivoSimulacion,
  ConfiguracionSimulacion,
  ConfiguracionSimulacionRequest,
  HistorialSimulacion,
  RutaSimulacion,
} from '../../../../../core/models';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { SesionStore } from '../../../../../core/services/sesion';
import { SimulacionApi } from '../../../../../core/services/simulacion.api';
import { ColumnasRedimensionables } from '../../../../../shared/directives/columnas-redimensionables.directive';
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../shared/ui/paginador/paginador';
import { SelectBuscable } from '../../../../../shared/ui/select-buscable/select-buscable';
import { colorCluster, formatearHoras, formatearNumero } from '../../simulacion-formato';

@Component({
  selector: 'app-simulacion-configuracion',
  imports: [
    HasPermiso,
    ColumnasRedimensionables,
    EmptyState,
    Icon,
    Modal,
    Paginador,
    SelectBuscable,
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

  readonly velocidad = signal(100);
  readonly limites = signal<Record<number, number>>({});
  readonly operarios = signal<Record<number, number>>({});
  readonly dirty = signal(false);

  readonly filtroChute = signal<number | null>(null);
  readonly filtroCluster = signal<number | null>(null);
  readonly filtroRuta = signal<number | null>(null);

  readonly pagina = signal(1);
  readonly porPagina = signal(25);

  readonly guardando = signal(false);
  readonly balanceando = signal(false);
  readonly restableciendo = signal(false);

  readonly historial = signal<HistorialSimulacion[]>([]);
  readonly historialAbierto = signal(false);
  readonly historialCargando = signal(false);

  readonly restablecerAbierto = signal(false);

  readonly moverAbierto = signal(false);
  readonly rutaAMover = signal<RutaSimulacion | null>(null);
  readonly chuteDestino = signal<number | null>(null);
  readonly moviendo = signal(false);

  readonly opcionesRegistro = computed(() =>
    this.registros().map((registro) => {
      const etiqueta =
        registro.etiqueta && registro.etiqueta !== 'SIN ETIQUETA' ? registro.etiqueta : null;
      return {
        id: registro.id,
        etiqueta: etiqueta ? `${registro.nombreRegistro} · ${etiqueta}` : registro.nombreRegistro,
      };
    }),
  );

  private readonly compararNombre = (a: string, b: string): number =>
    a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });

  readonly opcionesChute = computed(() =>
    [...(this.config()?.chutes ?? [])]
      .sort((a, b) => this.compararNombre(a.nombre, b.nombre))
      .map((chute) => ({ id: chute.id, etiqueta: chute.nombre })),
  );

  readonly opcionesCluster = computed(() =>
    [...(this.config()?.clusters ?? [])]
      .sort((a, b) => this.compararNombre(a.nombre, b.nombre))
      .map((cluster) => ({ id: cluster.id, etiqueta: cluster.nombre })),
  );

  readonly opcionesRuta = computed(() =>
    [...(this.config()?.rutas ?? [])]
      .sort((a, b) => this.compararNombre(a.codigoRuta, b.codigoRuta))
      .map((ruta) => ({ id: ruta.id, etiqueta: `${ruta.codigoRuta} · ${ruta.chute}` })),
  );

  readonly opcionesDestino = computed(() => {
    const ruta = this.rutaAMover();
    return [...(this.config()?.chutes ?? [])]
      .filter((chute) => chute.id !== ruta?.chuteId)
      .sort((a, b) => this.compararNombre(a.nombre, b.nombre))
      .map((chute) => ({ id: chute.id, etiqueta: chute.nombre }));
  });

  readonly filtrosActivos = computed(
    () =>
      (this.filtroChute() !== null ? 1 : 0) +
      (this.filtroCluster() !== null ? 1 : 0) +
      (this.filtroRuta() !== null ? 1 : 0),
  );

  readonly filas = computed(() => {
    const rutas = this.config()?.rutas ?? [];
    const chute = this.filtroChute();
    const cluster = this.filtroCluster();
    const ruta = this.filtroRuta();
    return rutas.filter(
      (fila) =>
        (chute === null || fila.chuteId === chute) &&
        (cluster === null || fila.clusterId === cluster) &&
        (ruta === null || fila.id === ruta),
    );
  });

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.filas().length / this.porPagina())),
  );
  readonly paginaActual = computed(() => Math.min(Math.max(1, this.pagina()), this.totalPaginas()));
  readonly filasPagina = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.porPagina();
    return this.filas().slice(inicio, inicio + this.porPagina());
  });

  readonly tieneLimites = computed(() =>
    Object.values(this.limites()).some((limite) => Number(limite) > 0),
  );

  readonly bultosPorChute = computed(() => {
    const mapa = new Map<number, number>();
    for (const ruta of this.config()?.rutas ?? []) {
      mapa.set(ruta.chuteId, (mapa.get(ruta.chuteId) ?? 0) + Number(ruta.totalBultos));
    }
    return mapa;
  });

  readonly resumenClusters = computed(() => {
    const cfg = this.config();
    if (!cfg) {
      return [];
    }
    const operarios = this.operarios();
    const limites = this.limites();
    const velocidad = this.velocidad() || 100;
    const bultosPorChute = this.bultosPorChute();

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
    for (const ruta of cfg.rutas) {
      const actual = acumulado.get(ruta.clusterId) ?? {
        bultos: 0,
        unidades: 0,
        lineas: 0,
        cod: 0,
        rutas: 0,
        chutes: new Set<number>(),
      };
      actual.bultos += Number(ruta.totalBultos);
      actual.unidades += Number(ruta.totalUnidades);
      actual.lineas += Number(ruta.totalLineas);
      actual.cod += ruta.conteoCod;
      actual.rutas += 1;
      actual.chutes.add(ruta.chuteId);
      acumulado.set(ruta.clusterId, actual);
    }

    return cfg.clusters
      .map((cluster, indice) => {
        const datos = acumulado.get(cluster.id);
        let tiempo = 0;
        for (const chuteId of datos?.chutes ?? []) {
          const ops = Math.max(1, operarios[chuteId] ?? 2);
          const tiempoChute = (bultosPorChute.get(chuteId) ?? 0) / (velocidad * ops);
          tiempo = Math.max(tiempo, tiempoChute);
        }
        const limite = limites[cluster.id] ?? 0;
        return {
          id: cluster.id,
          nombre: cluster.nombre,
          color: colorCluster(indice),
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
      })
      .sort((a, b) => a.nombre.localeCompare(b.nombre, undefined, { numeric: true }));
  });

  readonly nombreSeleccionado = computed(() => {
    const id = this.seleccionado();
    return this.registros().find((registro) => registro.id === id) ?? null;
  });

  constructor() {
    this.cargarOpciones();
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
        error: (err: unknown) => {
          this.errorOpciones.set(mensajeDeError(err));
          this.notificaciones.error('No se pudieron cargar los registros', mensajeDeError(err));
        },
      });
  }

  seleccionarDesdeSelector(id: number | null): void {
    if (this.guardando() || this.balanceando() || this.moviendo() || this.restableciendo()) {
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
    if (this.guardando() || this.balanceando() || this.moviendo() || this.restableciendo()) {
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
    this.seleccionado.set(id);
    this.config.set(null);
    this.error.set(null);
    this.limpiarFiltros();
    this.pagina.set(1);
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
    const solicitud = ++this.solicitud;
    this.cargando.set(true);
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
          }
        },
        error: (err: unknown) => {
          if (solicitud !== this.solicitud) {
            return;
          }
          this.error.set(mensajeDeError(err));
          this.notificaciones.error('No se pudo cargar la configuración', mensajeDeError(err));
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

  getOperarios(chuteId: number): number {
    return this.operarios()[chuteId] ?? 2;
  }

  colorDeCluster(clusterId: number): string {
    const indice = (this.config()?.clusters ?? []).findIndex((cluster) => cluster.id === clusterId);
    return colorCluster(Math.max(0, indice));
  }

  /** Tiempo que aporta esta ruta al chute (no el total acumulado del chute). */
  tiempoRuta(ruta: RutaSimulacion): number {
    const velocidad = this.velocidad() || 100;
    const ops = Math.max(1, this.getOperarios(ruta.chuteId));
    return Number(ruta.totalBultos) / (velocidad * ops);
  }

  setVelocidad(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const valor = Number(input.value);
    if (!Number.isFinite(valor) || valor <= 0) {
      input.value = String(this.velocidad());
      return;
    }
    this.velocidad.set(valor);
    this.actualizarDirty();
  }

  setLimite(clusterId: number, evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const valor = Number(input.value);
    const limite = Number.isFinite(valor) && valor >= 0 ? valor : 0;
    input.value = String(limite);
    const limites = { ...this.limites() };
    limites[clusterId] = limite;
    this.limites.set(limites);
    this.actualizarDirty();
  }

  setOperarios(chuteId: number, evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const valor = Number.parseInt(input.value, 10);
    const operariosAsignados = Number.isInteger(valor) && valor >= 1 ? valor : 1;
    input.value = String(operariosAsignados);
    const operarios = { ...this.operarios() };
    operarios[chuteId] = operariosAsignados;
    this.operarios.set(operarios);
    this.actualizarDirty();
  }

  /**
   * Los cambios se marcan comparando con lo último cargado del servidor: editar
   * un valor y dejarlo igual (o reescribir el mismo número) no debe bloquear el
   * cambio de registro ni la salida al dashboard.
   */
  private actualizarDirty(): void {
    const config = this.config();
    if (!config) {
      this.dirty.set(false);
      return;
    }
    const velocidadIgual = Number(config.velocidadBase) === this.velocidad();
    const limites = this.limites();
    const clustersIguales = config.clusters.every(
      (cluster) => Number(cluster.limiteHoras) === (limites[cluster.id] ?? 0),
    );
    const operarios = this.operarios();
    const chutesIguales = config.chutes.every(
      (chute) => chute.operarios === (operarios[chute.id] ?? 2),
    );
    this.dirty.set(!(velocidadIgual && clustersIguales && chutesIguales));
  }

  setFiltroChute(id: number | null): void {
    this.filtroChute.set(id);
    this.pagina.set(1);
  }

  setFiltroCluster(id: number | null): void {
    this.filtroCluster.set(id);
    this.pagina.set(1);
  }

  setFiltroRuta(id: number | null): void {
    this.filtroRuta.set(id);
    this.pagina.set(1);
  }

  limpiarFiltros(): void {
    this.filtroChute.set(null);
    this.filtroCluster.set(null);
    this.filtroRuta.set(null);
    this.pagina.set(1);
  }

  cambiarPagina(pagina: number): void {
    this.pagina.set(pagina);
  }

  cambiarPorPagina(valor: number): void {
    this.porPagina.set(valor);
    this.pagina.set(1);
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
        operarios: Math.max(1, this.operarios()[chute.id] ?? 2),
      })),
    };
  }

  guardar(): void {
    const id = this.seleccionado();
    const peticion = this.peticion();
    if (!id || !peticion || this.guardando()) {
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
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron guardar los parámetros', mensajeDeError(err)),
      });
  }

  balancear(): void {
    const id = this.seleccionado();
    const peticion = this.peticion();
    if (!id || !peticion || this.balanceando()) {
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
      error: (err: unknown) =>
        this.notificaciones.error('No se pudo ejecutar el balanceo', mensajeDeError(err)),
    });
  }

  private cargarConfiguracion(id: number): void {
    if (this.seleccionado() !== id) {
      return;
    }
    const solicitud = ++this.solicitud;
    this.cargando.set(true);
    this.error.set(null);
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
          }
        },
        error: (err: unknown) => {
          if (solicitud !== this.solicitud) {
            return;
          }
          this.notificaciones.error('No se pudo recargar la configuración', mensajeDeError(err));
        },
      });
  }

  verCambios(): void {
    const id = this.seleccionado();
    if (!id) {
      return;
    }
    this.historialAbierto.set(true);
    this.historialCargando.set(true);
    this.api
      .historial(id)
      .pipe(finalize(() => this.historialCargando.set(false)))
      .subscribe({
        next: (historial) => this.historial.set(historial),
        error: (err: unknown) => {
          this.historialAbierto.set(false);
          this.notificaciones.error('No se pudo cargar el historial', mensajeDeError(err));
        },
      });
  }

  confirmarRestablecer(): void {
    this.restablecerAbierto.set(true);
  }

  restablecer(): void {
    const id = this.seleccionado();
    if (!id || this.restableciendo()) {
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
        error: (err: unknown) =>
          this.notificaciones.error('No se pudo restablecer', mensajeDeError(err)),
      });
  }

  abrirMover(ruta: RutaSimulacion): void {
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
    if (!id || !ruta || destino === null || !peticion || this.moviendo()) {
      return;
    }
    this.moviendo.set(true);
    // Mover devuelve la configuración del servidor: si hay parámetros sin
    // guardar, se envían primero para no perderlos al refrescar la respuesta.
    const operacion = this.dirty()
      ? this.api
          .guardarConfiguracion(id, peticion)
          .pipe(switchMap(() => this.api.moverRuta(id, ruta.id, destino)))
      : this.api.moverRuta(id, ruta.id, destino);
    operacion.pipe(finalize(() => this.moviendo.set(false))).subscribe({
      next: (config) => {
        if (this.seleccionado() === id) {
          this.aplicarConfiguracion(config);
        }
        this.cerrarMover();
        this.notificaciones.exito('Ruta movida', `La ruta ${ruta.codigoRuta} cambió de chute.`);
      },
      error: (err: unknown) =>
        this.notificaciones.error('No se pudo mover la ruta', mensajeDeError(err)),
    });
  }

  irADashboard(): void {
    if (this.dirty()) {
      this.notificaciones.advertencia(
        'Cambios sin guardar',
        'Guarda los parámetros antes de salir de la configuración.',
      );
      return;
    }
    const id = this.seleccionado();
    void this.router.navigate(['/simulacion/dashboard'], {
      queryParams: id ? { registro: id } : {},
    });
  }

  irARegistro(): void {
    void this.router.navigate(['/simulacion/registro']);
  }
}
