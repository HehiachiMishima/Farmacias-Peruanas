import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import {
  ArchivoSimulacion,
  ChuteComparativoSimulacion,
  DashboardSimulacion,
} from '../../../../../core/models';
import { SimulacionApi } from '../../../../../core/services/simulacion.api';
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { ComparativoChart, GrupoComparativo } from '../../../../../shared/ui/charts/comparativo-chart';
import { porcentaje } from '../../../../../shared/ui/charts/formato';
import { Kpi } from '../../../../../shared/ui/charts/kpi';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { SelectBuscable } from '../../../../../shared/ui/select-buscable/select-buscable';
import { etiquetaRegistro, formatearHoras, formatearNumero } from '../../simulacion-formato';

type Metrica = 'bultos' | 'unidades' | 'lineas' | 'cod';

/** Métricas de carga por chute: qué campos comparar y cómo se llaman. */
const METRICAS: ReadonlyArray<{
  id: Metrica;
  nombre: string;
  antes: (c: ChuteComparativoSimulacion) => number;
  despues: (c: ChuteComparativoSimulacion) => number;
}> = [
  { id: 'bultos', nombre: 'Bultos', antes: (c) => c.bultosOriginal, despues: (c) => c.bultosBalanceado },
  { id: 'unidades', nombre: 'Unidades', antes: (c) => c.unidadesOriginal, despues: (c) => c.unidadesBalanceado },
  { id: 'lineas', nombre: 'Líneas', antes: (c) => c.lineasOriginal, despues: (c) => c.lineasBalanceado },
  { id: 'cod', nombre: 'COD', antes: (c) => c.codOriginal, despues: (c) => c.codBalanceado },
];

/**
 * Dashboard ejecutivo de simulación: comparativo antes vs. después del
 * balanceo. Por chute se compara la carga (bultos, unidades, líneas, COD);
 * por cluster, solo el tiempo de cierre frente a su límite.
 */
@Component({
  selector: 'app-simulacion-dashboard',
  imports: [HasPermiso, Kpi, ComparativoChart, EmptyState, Icon, SelectBuscable],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class SimulacionDashboard {
  private readonly api = inject(SimulacionApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly permisos = PERMISOS;
  readonly metricas = METRICAS;
  readonly formatearHoras = formatearHoras;
  readonly formatearNumero = formatearNumero;
  /** Valores de carga en el gráfico: enteros con separador de miles. */
  readonly formatoCarga = (valor: number): string => formatearNumero(valor);

  readonly registros = signal<ArchivoSimulacion[]>([]);
  readonly seleccionado = signal<number | null>(null);
  readonly cargandoOpciones = signal(false);

  readonly dashboard = signal<DashboardSimulacion | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly errorOpciones = signal<string | null>(null);
  /** Descarta respuestas viejas si el usuario cambia de registro rápido. */
  private solicitud = 0;

  readonly metrica = signal<Metrica>('bultos');
  readonly metricaActual = computed(() => METRICAS.find((m) => m.id === this.metrica()) ?? METRICAS[0]);

  readonly opcionesRegistro = computed(() =>
    this.registros().map((registro) => ({ id: registro.id, etiqueta: etiquetaRegistro(registro) })),
  );

  readonly registroSeleccionado = computed(() => {
    const id = this.seleccionado();
    return this.registros().find((registro) => registro.id === id) ?? null;
  });

  /** BALANCEADO = hay ajustes (balanceo o movimientos manuales) que comparar. */
  readonly estaBalanceado = computed(() => this.registroSeleccionado()?.estado === 'BALANCEADO');

  readonly tarjetas = computed(() => {
    const kpis = this.dashboard()?.kpis;
    if (!kpis) {
      return [];
    }
    return [
      {
        etiqueta: 'Cierre de la operación',
        valor: formatearHoras(kpis.tiempoBalanceado),
        icono: 'reloj',
        color: 'var(--accent)',
        detalle: `Antes ${formatearHoras(kpis.tiempoOriginal)} · cluster más lento`,
      },
      {
        etiqueta: 'Tiempo ahorrado',
        valor: formatearHoras(kpis.tiempoAhorrado),
        icono: 'tendencia',
        color: 'var(--success)',
        detalle:
          kpis.tiempoOriginal > 0
            ? `${porcentaje(kpis.tiempoAhorrado / kpis.tiempoOriginal)} menos que la proyección`
            : 'Sin proyección de referencia',
      },
      {
        etiqueta: 'Ajustes realizados',
        valor: `${formatearNumero(kpis.rutasMovidas)} rutas`,
        icono: 'flecha',
        color: 'var(--violet)',
        detalle: `${formatearNumero(kpis.operariosAgregados)} operario(s) añadido(s)`,
      },
    ];
  });

  /** Carga por chute (métrica elegida): antes vs. después. */
  readonly gruposChute = computed<GrupoComparativo[]>(() => {
    const metrica = this.metricaActual();
    return (this.dashboard()?.chutes ?? []).map((chute) => ({
      etiqueta: chute.chute,
      antes: Number(metrica.antes(chute)),
      despues: Number(metrica.despues(chute)),
    }));
  });

  /** Tiempo de cierre por cluster: antes vs. después, con su límite. */
  readonly gruposCluster = computed<GrupoComparativo[]>(() =>
    (this.dashboard()?.clusters ?? []).map((cluster) => ({
      etiqueta: cluster.cluster,
      antes: Number(cluster.tiempoOriginal),
      despues: Number(cluster.tiempoBalanceado),
      limite: Number(cluster.limiteHoras),
    })),
  );

  /** Totales de las cuatro métricas (fila final del cuadro por chute). */
  readonly totalesChute = computed(() => {
    const chutes = this.dashboard()?.chutes ?? [];
    return METRICAS.map((m) => ({
      antes: chutes.reduce((total, chute) => total + Number(m.antes(chute)), 0),
      despues: chutes.reduce((total, chute) => total + Number(m.despues(chute)), 0),
    }));
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
            this.solicitud++;
            this.seleccionado.set(null);
            this.dashboard.set(null);
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

  /**
   * Recarga también la lista de registros: el estado puede haber cambiado en
   * otra pestaña y de él depende que se muestre el comparativo.
   */
  actualizar(): void {
    this.cargarOpciones();
  }

  seleccionar(id: number | null): void {
    this.seleccionado.set(id);
    this.dashboard.set(null);
    this.error.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { registro: id },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.cargar();
  }

  /** Solo pide el comparativo si hay ajustes: en proyección no hay nada que comparar. */
  cargar(): void {
    const id = this.seleccionado();
    const solicitud = ++this.solicitud;
    if (!id || !this.estaBalanceado()) {
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.api
      .dashboard(id)
      .pipe(
        finalize(() => {
          if (solicitud === this.solicitud) {
            this.cargando.set(false);
          }
        }),
      )
      .subscribe({
        next: (dashboard) => {
          if (solicitud === this.solicitud) {
            this.dashboard.set(dashboard);
          }
        },
        error: (err: unknown) => {
          if (solicitud === this.solicitud) {
            this.error.set(mensajeDeError(err));
          }
        },
      });
  }

  /** Variación relativa «+12 %» / «−8 %»; «—» si no había base. */
  variacion(antes: number, despues: number): string {
    if (antes <= 0) {
      return despues > 0 ? 'nuevo' : '—';
    }
    const relativa = (despues - antes) / antes;
    return Math.abs(relativa) < 0.0005 ? '0 %' : `${relativa > 0 ? '+' : ''}${porcentaje(relativa)}`;
  }

  /**
   * Color de la variación. En tiempo, bajar es bueno (verde) y subir es malo
   * (ámbar); en carga, mover bultos no es bueno ni malo: solo se marca el sentido.
   */
  claseVariacion(antes: number, despues: number, bajarEsBueno: boolean): string {
    if (Math.abs(despues - antes) < 0.005) {
      return '';
    }
    const baja = despues < antes;
    if (!bajarEsBueno) {
      return baja ? 'dash-delta--menos' : 'dash-delta--mas';
    }
    return baja ? 'dash-delta--baja' : 'dash-delta--sube';
  }

  irAConfiguracion(): void {
    const id = this.seleccionado();
    void this.router.navigate(['/simulacion/configuracion'], {
      queryParams: id ? { registro: id } : {},
    });
  }

  irARegistro(): void {
    void this.router.navigate(['/simulacion/registro']);
  }
}
