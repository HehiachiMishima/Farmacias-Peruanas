import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PANEL_ADMIN } from '../../../../../core/config/paneles';
import { AnaliticaAdmin } from '../../../../../core/models';
import { AdminAnaliticaApi } from '../../../../../core/services/admin-analitica.api';
import { Auth } from '../../../../../core/services/auth';
import { RecursoRemoto } from '../../../../../core/utils/listado-remoto';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { SERIES, entero, porcentaje } from '../../../../../shared/ui/charts/formato';
import { GaugeChart } from '../../../../../shared/ui/charts/gauge-chart';
import { Kpi } from '../../../../../shared/ui/charts/kpi';
import { LineChart, SerieLinea } from '../../../../../shared/ui/charts/line-chart';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';

const NOMBRE_PANEL: Record<string, string> = {
  PANEL_ADMIN: 'Administración',
  PANEL_BITACORA: 'Bitácora',
  PANEL_INBOUND: 'Inbound',
  PANEL_SIMULACION: 'Simulación',
};

const COLOR_PANEL: Record<string, string> = {
  PANEL_ADMIN: 'var(--violet)',
  PANEL_BITACORA: 'var(--brand)',
  PANEL_INBOUND: 'var(--accent)',
  PANEL_SIMULACION: 'var(--cyan)',
};

/** Roles visibles en el cuadro de reparto; el resto se agrupa en «Otros». */
const MAX_ROLES = 4;

/**
 * Dashboard ejecutivo de Administración: salud del gobierno de accesos.
 *
 * Tres indicadores (cuentas activas, usuarios sin rol, excepciones) y cuatro
 * cuadros: cuentas habilitadas, acceso por panel, reparto por rol y altas.
 */
@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, BarChart, EmptyState, GaugeChart, Icon, Kpi, LineChart],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly auth = inject(Auth);
  private readonly api = inject(AdminAnaliticaApi);

  private readonly permisos = this.auth.permisos;
  readonly usuario = this.auth.usuario;
  readonly panelRuta = PANEL_ADMIN.ruta;

  private readonly recurso = new RecursoRemoto<AnaliticaAdmin>(() => this.api.analitica());
  readonly cargando = this.recurso.cargando;
  readonly error = this.recurso.error;
  readonly datos = this.recurso.valor;

  readonly modulos = computed(() =>
    PANEL_ADMIN.modulos.filter((modulo) => this.permisos().includes(modulo.permiso)),
  );

  readonly saludo = computed(() => {
    const hora = new Date().getHours();
    return hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
  });

  readonly kpis = computed(() => this.datos()?.kpis ?? null);

  readonly tarjetas = computed(() => {
    const k = this.kpis();
    const excepciones = k ? k.overridesGrant + k.overridesDeny : 0;
    return [
      {
        etiqueta: 'Cuentas activas',
        valor: k ? `${entero(k.usuariosActivos)} de ${entero(k.usuarios)}` : '—',
        icono: 'usuarios',
        color: 'var(--accent)',
        detalle: k ? `${porcentaje(k.usuarios ? k.usuariosActivos / k.usuarios : 0)} pueden iniciar sesión` : '',
      },
      {
        etiqueta: 'Usuarios sin rol',
        valor: k ? entero(k.usuariosSinRol) : '—',
        icono: 'alertTriangle',
        color: k && k.usuariosSinRol > 0 ? 'var(--warning)' : 'var(--success)',
        detalle: k && k.usuariosSinRol > 0 ? 'Revisar: acceso solo por excepción' : 'Todos tienen un rol',
      },
      {
        etiqueta: 'Excepciones al RBAC',
        valor: k ? entero(excepciones) : '—',
        icono: 'key',
        color: excepciones > 0 ? 'var(--brand)' : 'var(--success)',
        detalle: k ? `${entero(k.overridesGrant)} concedidas · ${entero(k.overridesDeny)} denegadas` : '',
      },
    ];
  });

  /** % de cuentas habilitadas (gauge) y su desglose. */
  readonly estadoCuentas = computed(() => {
    const k = this.kpis();
    const total = k?.usuarios ?? 0;
    const activos = k?.usuariosActivos ?? 0;
    return {
      pct: total ? Math.round((activos / total) * 100) : 0,
      activos: entero(activos),
      inactivos: entero(total - activos),
      hay: total > 0,
    };
  });

  /** Top de roles por usuarios; los demás se suman en «Otros». */
  readonly barrasRoles = computed<BarraDato[]>(() => {
    const roles = (this.datos()?.usuariosPorRol ?? []).filter((c) => c.valor > 0);
    const total = this.kpis()?.usuarios ?? 0;
    const visibles = roles.slice(0, MAX_ROLES);
    const resto = roles.slice(MAX_ROLES).reduce((suma, c) => suma + c.valor, 0);
    const lista = resto > 0 ? [...visibles, { etiqueta: 'Otros', valor: resto }] : visibles;
    return lista.map((c, i) => ({
      etiqueta: c.etiqueta,
      valor: c.valor,
      color: c.etiqueta === 'Otros' ? 'var(--chart-otros)' : SERIES[i % SERIES.length],
      detalle: porcentaje(total ? c.valor / total : 0),
    }));
  });

  /** Usuarios activos con acceso a cada panel, en % del total de activos. */
  readonly barrasCobertura = computed<BarraDato[]>(() => {
    const activos = this.kpis()?.usuariosActivos ?? 0;
    const porPanel = new Map((this.datos()?.coberturaPaneles ?? []).map((c) => [c.etiqueta, c.valor]));
    return Object.keys(NOMBRE_PANEL).map((clave) => {
      const valor = porPanel.get(clave) ?? 0;
      return {
        etiqueta: NOMBRE_PANEL[clave],
        valor,
        color: COLOR_PANEL[clave],
        detalle: `${porcentaje(activos ? valor / activos : 0)} de los activos`,
      };
    });
  });

  readonly altas = computed(() => {
    const serie = this.datos()?.altasPorMes ?? [];
    return {
      etiquetas: serie.map((p) => p.periodo),
      series: [
        { nombre: 'Altas', color: 'var(--accent)', valores: serie.map((p) => p.valor) },
        { nombre: 'Siguen activas', color: 'var(--success)', valores: serie.map((p) => p.secundario) },
      ] as SerieLinea[],
    };
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.recurso.recargar();
  }
}
