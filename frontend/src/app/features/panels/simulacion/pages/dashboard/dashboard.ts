import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { ArchivoSimulacion, DashboardSimulacion } from '../../../../../core/models';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { SimulacionApi } from '../../../../../core/services/simulacion.api';
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { Kpi } from '../../../../../shared/ui/charts/kpi';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { SelectBuscable } from '../../../../../shared/ui/select-buscable/select-buscable';
import { formatearHoras, formatearNumero } from '../../simulacion-formato';

@Component({
  selector: 'app-simulacion-dashboard',
  imports: [HasPermiso, Kpi, EmptyState, Icon, SelectBuscable],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class SimulacionDashboard {
  private readonly api = inject(SimulacionApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly permisos = PERMISOS;
  readonly formatearHoras = formatearHoras;
  readonly formatearNumero = formatearNumero;

  readonly registros = signal<ArchivoSimulacion[]>([]);
  readonly seleccionado = signal<number | null>(null);
  readonly cargandoOpciones = signal(false);

  readonly dashboard = signal<DashboardSimulacion | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly errorOpciones = signal<string | null>(null);
  /** Descarta respuestas viejas si el usuario cambia de registro rápido. */
  private solicitud = 0;

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

  readonly registroSeleccionado = computed(() => {
    const id = this.seleccionado();
    return this.registros().find((registro) => registro.id === id) ?? null;
  });

  readonly estaBalanceado = computed(() => this.registroSeleccionado()?.estado === 'BALANCEADO');

  readonly tarjetas = computed(() => {
    const kpis = this.dashboard()?.kpis;
    return [
      {
        etiqueta: 'Ahorro de tiempo',
        valor: kpis ? formatearHoras(kpis.tiempoAhorrado) : '—',
        icono: 'reloj',
        color: 'var(--success)',
        detalle: 'Reducción del cuello de botella',
      },
      {
        etiqueta: 'Rutas movidas',
        valor: kpis ? formatearNumero(kpis.rutasMovidas) : '—',
        icono: 'flecha',
        color: 'var(--accent)',
        detalle: 'Movimientos manuales y del balanceo',
      },
      {
        etiqueta: 'Operarios añadidos',
        valor: kpis ? formatearNumero(kpis.operariosAgregados) : '—',
        icono: 'usuarios',
        color: 'var(--violet)',
        detalle: 'Para aliviar los chutes cuello de botella',
      },
      {
        etiqueta: 'Bultos totales',
        valor: kpis ? formatearNumero(kpis.bultos) : '—',
        icono: 'paquete',
        color: 'var(--brand)',
        detalle: kpis
          ? `${formatearNumero(kpis.unidades)} u · ${formatearNumero(kpis.lineas)} líneas · ${formatearNumero(kpis.cod)} COD`
          : '',
      },
    ];
  });

  /**
   * Vista ejecutiva del cierre por cluster: una barra por cluster con su
   * tiempo balanceado, el ahorro y una marca en el límite configurado.
   * Todas las barras comparten escala para que sean comparables entre sí.
   */
  readonly cierres = computed(() => {
    const clusters = this.dashboard()?.clusters ?? [];
    const escala = Math.max(
      0.01,
      ...clusters.map((cluster) =>
        Math.max(cluster.tiempoOriginal, cluster.tiempoBalanceado, cluster.limiteHoras),
      ),
    );
    return clusters.map((cluster) => ({
      cluster: cluster.cluster,
      tiempoOriginal: cluster.tiempoOriginal,
      tiempoBalanceado: cluster.tiempoBalanceado,
      limiteHoras: cluster.limiteHoras,
      excede: cluster.excede,
      pctBalanceado: Math.min(100, (cluster.tiempoBalanceado / escala) * 100),
      pctLimite: cluster.limiteHoras > 0 ? Math.min(100, (cluster.limiteHoras / escala) * 100) : 0,
      ahorro: Math.max(0, cluster.tiempoOriginal - cluster.tiempoBalanceado),
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
        error: (err: unknown) => {
          this.errorOpciones.set(mensajeDeError(err));
          this.notificaciones.error('No se pudieron cargar los registros', mensajeDeError(err));
        },
      });
  }

  /**
   * Recarga también la lista de registros: el estado «Balanceado» puede haber
   * cambiado en otra pestaña y de él depende que se muestre el comparativo.
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

  cargar(): void {
    const id = this.seleccionado();
    const solicitud = ++this.solicitud;
    if (!id) {
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
          if (solicitud !== this.solicitud) {
            return;
          }
          this.error.set(mensajeDeError(err));
          this.notificaciones.error('No se pudo cargar el dashboard', mensajeDeError(err));
        },
      });
  }

  estadoChute(estado: string): { texto: string; clase: string } {
    if (estado === 'MEJORO') {
      return { texto: 'Mejoró', clase: 'ui-badge--success' };
    }
    if (estado === 'AUMENTO') {
      return { texto: 'Aumentó', clase: 'ui-badge--warning' };
    }
    return { texto: 'Igual', clase: 'ui-badge--neutral' };
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
