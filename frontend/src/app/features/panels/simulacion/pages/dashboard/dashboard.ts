import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { PERMISOS } from '../../../../../core/config/permisos';
import { mensajeDeError } from '../../../../../core/errors/error.util';
import { ArchivoSimulacion, DashboardSimulacion } from '../../../../../core/models';
import { SimulacionApi } from '../../../../../core/services/simulacion.api';
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { BulletChart, ItemBullet } from '../../../../../shared/ui/charts/bullet-chart';
import { porcentaje } from '../../../../../shared/ui/charts/formato';
import { Kpi } from '../../../../../shared/ui/charts/kpi';
import { RingChart, SegmentoAnillo } from '../../../../../shared/ui/charts/ring-chart';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { SelectBuscable } from '../../../../../shared/ui/select-buscable/select-buscable';
import { etiquetaRegistro, formatearHoras, formatearNumero } from '../../simulacion-formato';

interface Hallazgo {
  kicker: string;
  texto: string;
  icono: string;
  color: string;
}

@Component({
  selector: 'app-simulacion-dashboard',
  imports: [HasPermiso, Kpi, BarChart, BulletChart, RingChart, EmptyState, Icon, SelectBuscable],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class SimulacionDashboard {
  private readonly api = inject(SimulacionApi);
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
    this.registros().map((registro) => ({ id: registro.id, etiqueta: etiquetaRegistro(registro) })),
  );

  readonly registroSeleccionado = computed(() => {
    const id = this.seleccionado();
    return this.registros().find((registro) => registro.id === id) ?? null;
  });

  /** BALANCEADO = hay ajustes (balanceo o movimientos manuales) que comparar. */
  readonly estaBalanceado = computed(() => this.registroSeleccionado()?.estado === 'BALANCEADO');

  readonly tarjetas = computed(() => {
    const datos = this.dashboard();
    if (!datos) {
      return [];
    }
    const { kpis, clusters } = datos;
    const conLimite = clusters.filter((cluster) => cluster.limiteHoras > 0);
    const cumplen = conLimite.filter((cluster) => !cluster.excede).length;
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
        etiqueta: 'Clusters en su límite',
        valor: conLimite.length ? `${cumplen} de ${conLimite.length}` : '—',
        icono: 'checkCircleFilled',
        color: cumplen === conLimite.length ? 'var(--success)' : 'var(--danger)',
        detalle: !conLimite.length
          ? 'Sin límites definidos'
          : cumplen === conLimite.length
            ? 'Todos cumplen su límite'
            : `${conLimite.length - cumplen} todavía ${conLimite.length - cumplen === 1 ? 'excede' : 'exceden'}`,
      },
      {
        etiqueta: 'Rutas movidas',
        valor: formatearNumero(kpis.rutasMovidas),
        icono: 'flecha',
        color: 'var(--cyan)',
        detalle: 'Entre chutes (balanceo y manual)',
      },
      {
        etiqueta: 'Operarios añadidos',
        valor: formatearNumero(kpis.operariosAgregados),
        icono: 'usuarios',
        color: 'var(--violet)',
        detalle: 'En los chutes cuello de botella',
      },
      {
        etiqueta: 'Bultos proyectados',
        valor: formatearNumero(kpis.bultos),
        icono: 'paquete',
        color: 'var(--brand)',
        detalle: `${formatearNumero(kpis.unidades)} unidades · ${formatearNumero(kpis.cod)} COD`,
      },
    ];
  });

  /** Tres conclusiones en lenguaje de negocio, calculadas de los mismos datos. */
  readonly hallazgos = computed<Hallazgo[]>(() => {
    const datos = this.dashboard();
    if (!datos || datos.clusters.length === 0) {
      return [];
    }
    const lista: Hallazgo[] = [];
    const masLento = [...datos.clusters].sort((a, b) => b.tiempoBalanceado - a.tiempoBalanceado)[0];
    lista.push({
      kicker: 'Marca el cierre',
      texto: `<strong>${escapar(masLento.cluster)}</strong> termina en <strong>${formatearHoras(masLento.tiempoBalanceado)}</strong>${
        masLento.limiteHoras > 0 ? ` con un límite de ${formatearHoras(masLento.limiteHoras)}` : ''
      }.`,
      icono: 'reloj',
      color: masLento.excede ? 'var(--danger)' : 'var(--accent)',
    });

    const cuello = [...datos.chutes].sort((a, b) => b.tiempoBalanceado - a.tiempoBalanceado)[0];
    if (cuello) {
      lista.push({
        kicker: 'Chute más cargado',
        texto: `<strong>${escapar(cuello.chute)}</strong> necesita <strong>${formatearHoras(cuello.tiempoBalanceado)}</strong> con ${cuello.operariosBalanceado} operario(s).`,
        icono: 'alerta',
        color: 'var(--warning)',
      });
    }

    const mejoraron = datos.chutes.filter((chute) => chute.estado === 'MEJORO').length;
    lista.push({
      kicker: 'Resultado del ajuste',
      texto:
        datos.kpis.tiempoAhorrado > 0.01
          ? `El cierre baja <strong>${formatearHoras(datos.kpis.tiempoAhorrado)}</strong> y <strong>${mejoraron}</strong> chute(s) reducen su tiempo.`
          : 'Los ajustes no cambian el tiempo de cierre de la operación.',
      icono: 'tendencia',
      color: datos.kpis.tiempoAhorrado > 0.01 ? 'var(--success)' : 'var(--text-3)',
    });
    return lista;
  });

  /**
   * Cierre por cluster: barra del tiempo balanceado sobre la del original
   * (atenuada) y una marca en el límite. Todas comparten escala.
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
      pctOriginal: Math.min(100, (cluster.tiempoOriginal / escala) * 100),
      pctBalanceado: Math.min(100, (cluster.tiempoBalanceado / escala) * 100),
      pctLimite: cluster.limiteHoras > 0 ? Math.min(100, (cluster.limiteHoras / escala) * 100) : 0,
      ahorro: Math.max(0, cluster.tiempoOriginal - cluster.tiempoBalanceado),
    }));
  });

  readonly cargaClusters = computed<SegmentoAnillo[]>(() =>
    (this.dashboard()?.clusters ?? []).map((cluster) => ({
      etiqueta: cluster.cluster,
      valor: Number(cluster.bultosBalanceado),
    })),
  );

  /** Tiempo de cada chute frente al promedio de los chutes con carga. */
  readonly tiemposChute = computed(() => {
    const chutes = (this.dashboard()?.chutes ?? []).filter((chute) => chute.tiempoBalanceado > 0);
    const promedio = chutes.length
      ? chutes.reduce((total, chute) => total + chute.tiempoBalanceado, 0) / chutes.length
      : 0;
    const items: ItemBullet[] = chutes.map((chute) => ({
      etiqueta: chute.chute,
      valor: chute.tiempoBalanceado,
      texto: formatearHoras(chute.tiempoBalanceado),
      detalle: `${chute.operariosBalanceado} operario(s) · ${formatearNumero(chute.bultosBalanceado)} bultos`,
    }));
    return { items, promedio, texto: formatearHoras(promedio) };
  });

  readonly operariosAnadidos = computed<BarraDato[]>(() =>
    (this.dashboard()?.chutes ?? [])
      .filter((chute) => chute.operariosBalanceado > chute.operariosOriginal)
      .sort(
        (a, b) =>
          b.operariosBalanceado - b.operariosOriginal - (a.operariosBalanceado - a.operariosOriginal),
      )
      .slice(0, 5)
      .map((chute) => ({
        etiqueta: chute.chute,
        valor: chute.operariosBalanceado - chute.operariosOriginal,
        color: 'var(--violet)',
        detalle: `${chute.operariosOriginal} → ${chute.operariosBalanceado} operarios`,
      })),
  );

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

  estadoChute(estado: string): { texto: string; clase: string } {
    if (estado === 'MEJORO') {
      return { texto: 'Mejoró', clase: 'ui-badge--success' };
    }
    if (estado === 'AUMENTO') {
      return { texto: 'Aumentó', clase: 'ui-badge--warning' };
    }
    return { texto: 'Igual', clase: 'ui-badge--neutral' };
  }

  /** Variación del tiempo del chute en % («−25 %»); vacío si no había tiempo base. */
  variacion(antes: number, despues: number): string {
    if (antes <= 0) {
      return '—';
    }
    const relativa = (despues - antes) / antes;
    return Math.abs(relativa) < 0.0005 ? '0 %' : `${relativa > 0 ? '+' : ''}${porcentaje(relativa)}`;
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

/** Los nombres de cluster/chute vienen del Excel: se escapan antes de usarlos en [innerHTML]. */
function escapar(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}
