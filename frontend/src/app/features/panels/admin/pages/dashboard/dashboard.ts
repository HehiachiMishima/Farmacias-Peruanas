import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PANEL_ADMIN } from '../../../../../core/config/paneles';
import { AnaliticaAdmin } from '../../../../../core/models';
import { AdminAnaliticaApi } from '../../../../../core/services/admin-analitica.api';
import { Auth } from '../../../../../core/services/auth';
import { RecursoRemoto } from '../../../../../core/utils/listado-remoto';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { DonutChart, SegmentoDonut } from '../../../../../shared/ui/charts/donut-chart';
import { SERIES, entero, porcentaje } from '../../../../../shared/ui/charts/formato';
import { Kpi } from '../../../../../shared/ui/charts/kpi';
import { LineChart, SerieLinea } from '../../../../../shared/ui/charts/line-chart';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';

const NOMBRE_PANEL: Record<string, string> = {
  PANEL_ADMIN: 'Administración',
  PANEL_BITACORA: 'Bitácora',
  PANEL_INBOUND: 'Inbound',
};

const COLOR_PANEL: Record<string, string> = {
  PANEL_ADMIN: 'var(--violet)',
  PANEL_BITACORA: 'var(--brand)',
  PANEL_INBOUND: 'var(--accent)',
};

/**
 * Dashboard ejecutivo de Administración: salud del gobierno de accesos.
 *
 * Responde: ¿cuántas cuentas están habilitadas y cuáles son un riesgo (sin
 * rol, excepciones)?, ¿cómo se reparten los usuarios por rol y por panel?,
 * ¿qué permisos están más extendidos? y ¿cómo crece la base de usuarios?
 */
@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, BarChart, DonutChart, EmptyState, Icon, Kpi, LineChart],
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
    const activosPct = k && k.usuarios ? k.usuariosActivos / k.usuarios : 0;
    return [
      {
        etiqueta: 'Usuarios',
        valor: k ? entero(k.usuarios) : '—',
        icono: 'usuarios',
        color: 'var(--accent)',
        detalle: k ? `${entero(k.usuariosActivos)} activos (${porcentaje(activosPct)})` : '',
      },
      {
        etiqueta: 'Cuentas inactivas',
        valor: k ? entero(k.usuarios - k.usuariosActivos) : '—',
        icono: 'power',
        color: 'var(--color-graphite)',
        detalle: 'Bloqueadas: no pueden iniciar sesión',
      },
      {
        etiqueta: 'Usuarios sin rol',
        valor: k ? entero(k.usuariosSinRol) : '—',
        icono: 'alertTriangle',
        color: k && k.usuariosSinRol > 0 ? 'var(--warning)' : 'var(--success)',
        detalle: k && k.usuariosSinRol > 0 ? 'Revisar: acceso solo por excepción' : 'Todos tienen un rol',
      },
      {
        etiqueta: 'Roles',
        valor: k ? entero(k.roles) : '—',
        icono: 'roles',
        color: 'var(--violet)',
        detalle: k ? `${entero(k.rolesActivos)} activos` : '',
      },
      {
        etiqueta: 'Permisos',
        valor: k ? entero(k.permisos) : '—',
        icono: 'permisos',
        color: 'var(--cyan)',
        detalle: k ? `${entero(k.permisosActivos)} activos en el catálogo` : '',
      },
      {
        etiqueta: 'Excepciones',
        valor: k ? entero(k.overridesGrant + k.overridesDeny) : '—',
        icono: 'key',
        color: 'var(--brand)',
        detalle: k ? `${entero(k.overridesGrant)} concedidas · ${entero(k.overridesDeny)} denegadas` : '',
      },
    ];
  });

  readonly hallazgos = computed(() => {
    const d = this.datos();
    if (!d) {
      return [];
    }
    const k = d.kpis;
    const salida: { icono: string; color: string; kicker: string; texto: string }[] = [];
    if (k.usuariosSinRol > 0) {
      salida.push({
        icono: 'alertTriangle',
        color: 'var(--warning)',
        kicker: 'Riesgo de gobierno',
        texto: `${entero(k.usuariosSinRol)} usuario(s) no tienen rol: su acceso depende de excepciones individuales o no tienen ninguno.`,
      });
    }
    const rol = d.usuariosPorRol[0];
    if (rol && rol.valor > 0) {
      salida.push({
        icono: 'roles',
        color: 'var(--violet)',
        kicker: 'Rol más asignado',
        texto: `${rol.etiqueta} agrupa a ${entero(rol.valor)} usuario(s) (${porcentaje(k.usuarios ? rol.valor / k.usuarios : 0)} del total).`,
      });
    }
    if (k.overridesDeny + k.overridesGrant > 0) {
      salida.push({
        icono: 'key',
        color: 'var(--brand)',
        kicker: 'Excepciones al RBAC',
        texto: `Hay ${entero(k.overridesGrant)} permisos concedidos y ${entero(k.overridesDeny)} denegados de forma individual; lo ideal es modelarlos como roles.`,
      });
    }
    return salida;
  });

  readonly segmentosEstado = computed<SegmentoDonut[]>(() => {
    const k = this.kpis();
    if (!k || k.usuarios === 0) {
      return [];
    }
    return [
      { etiqueta: 'Activos', valor: k.usuariosActivos, color: 'var(--success)' },
      { etiqueta: 'Inactivos', valor: k.usuarios - k.usuariosActivos, color: 'var(--danger)' },
    ].filter((s) => s.valor > 0);
  });

  readonly barrasRoles = computed<BarraDato[]>(() =>
    (this.datos()?.usuariosPorRol ?? []).slice(0, 10).map((c, i) => ({
      etiqueta: c.etiqueta,
      valor: c.valor,
      color: SERIES[i % SERIES.length],
      detalle: porcentaje(this.kpis()?.usuarios ? c.valor / this.kpis()!.usuarios : 0),
    })),
  );

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

  readonly barrasPermisos = computed<BarraDato[]>(() =>
    (this.datos()?.permisosMasOtorgados ?? []).map((c) => ({
      etiqueta: c.etiqueta,
      valor: c.valor,
      color: 'var(--cyan)',
      detalle: 'usuarios',
    })),
  );

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
