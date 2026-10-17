import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { PANEL_ADMIN } from '../../../../../core/config/paneles';
import { PERMISOS } from '../../../../../core/config/permisos';
import { Auth } from '../../../../../core/services/auth';
import { Permiso, Rol, Usuario } from '../../../../../core/models';
import { PermisosApi } from '../../../../../core/services/permisos.api';
import { RolesApi } from '../../../../../core/services/roles.api';
import { UsuariosApi } from '../../../../../core/services/usuarios.api';
import { BarChart, BarraDato } from '../../../../../shared/ui/charts/bar-chart';
import { DonutChart, SegmentoDonut } from '../../../../../shared/ui/charts/donut-chart';
import { GaugeChart } from '../../../../../shared/ui/charts/gauge-chart';
import { Icon } from '../../../../../shared/ui/icon/icon';

interface Metrica {
  id: string;
  etiqueta: string;
  icono: string;
  valor: number | null;
  detalle: string;
  acento: 'brand' | 'accent' | 'success';
}

@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, Icon, DonutChart, BarChart, GaugeChart],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly auth = inject(Auth);
  private readonly usuariosApi = inject(UsuariosApi);
  private readonly rolesApi = inject(RolesApi);
  private readonly permisosApi = inject(PermisosApi);

  private readonly permisos = this.auth.permisos;
  readonly usuario = this.auth.usuario;
  readonly panelRuta = PANEL_ADMIN.ruta;

  readonly cargando = signal(true);

  private readonly usuariosTotal = signal<number | null>(null);
  private readonly usuariosActivos = signal<number | null>(null);
  private readonly rolesTotal = signal<number | null>(null);
  private readonly permisosTotal = signal<number | null>(null);

  readonly metricas = computed<Metrica[]>(() => [
    {
      id: 'usuarios',
      etiqueta: 'Usuarios',
      icono: 'usuarios',
      valor: this.usuariosTotal(),
      detalle: this.usuariosActivos() !== null ? `${this.usuariosActivos()} activos` : 'Requiere permiso',
      acento: 'brand',
    },
    {
      id: 'roles',
      etiqueta: 'Roles',
      icono: 'roles',
      valor: this.rolesTotal(),
      detalle: this.rolesTotal() !== null ? 'Agrupadores de permisos' : 'Requiere permiso',
      acento: 'accent',
    },
    {
      id: 'permisos',
      etiqueta: 'Permisos',
      icono: 'permisos',
      valor: this.permisosTotal(),
      detalle: this.permisosTotal() !== null ? 'Catálogo del sistema' : 'Requiere permiso',
      acento: 'success',
    },
  ]);

  readonly modulos = computed(() =>
    PANEL_ADMIN.modulos.filter((modulo) => this.permisos().includes(modulo.permiso)),
  );

  readonly hayDatosUsuarios = computed(() => (this.usuariosTotal() ?? 0) > 0);

  readonly segmentosUsuarios = computed<SegmentoDonut[]>(() => {
    const total = this.usuariosTotal();
    if (total === null || total <= 0) {
      return [];
    }
    const activos = this.usuariosActivos() ?? 0;
    const inactivos = Math.max(0, total - activos);
    return [
      { etiqueta: 'Activos', valor: activos, color: 'var(--success)' },
      { etiqueta: 'Inactivos', valor: inactivos, color: 'var(--danger)' },
    ].filter((segmento) => segmento.valor > 0);
  });

  readonly pctActivos = computed(() => {
    const total = this.usuariosTotal();
    if (!total) {
      return 0;
    }
    return Math.round(((this.usuariosActivos() ?? 0) / total) * 100);
  });

  readonly barrasEntidades = computed<BarraDato[]>(() => {
    const items: BarraDato[] = [];
    const usuarios = this.usuariosTotal();
    const roles = this.rolesTotal();
    const permisos = this.permisosTotal();

    if (usuarios !== null) {
      items.push({ etiqueta: 'Usuarios', valor: usuarios, color: 'var(--brand)' });
    }
    if (roles !== null) {
      items.push({ etiqueta: 'Roles', valor: roles, color: 'var(--color-graphite)' });
    }
    if (permisos !== null) {
      items.push({ etiqueta: 'Permisos', valor: permisos, color: 'var(--success)' });
    }
    return items;
  });

  readonly hayBarras = computed(() => this.barrasEntidades().length > 0);

  readonly saludo = computed(() => {
    const hora = new Date().getHours();
    if (hora < 12) {
      return 'Buenos días';
    }
    if (hora < 19) {
      return 'Buenas tardes';
    }
    return 'Buenas noches';
  });

  constructor() {
    this.cargar();
  }

  private cargar(): void {
    const permisos = this.permisos();
    const puedeUsuarios = permisos.includes(PERMISOS.VER_USUARIOS);
    const puedeRoles = permisos.includes(PERMISOS.VER_ROLES);
    const puedePermisos = permisos.includes(PERMISOS.VER_PERMISOS);

    const usuarios$ = puedeUsuarios
      ? this.usuariosApi.listar().pipe(catchError(() => of([] as Usuario[])))
      : of([] as Usuario[]);
    const roles$ = puedeRoles
      ? this.rolesApi.listar().pipe(catchError(() => of([] as Rol[])))
      : of([] as Rol[]);
    const permisos$ = puedePermisos
      ? this.permisosApi.listar().pipe(catchError(() => of([] as Permiso[])))
      : of([] as Permiso[]);

    forkJoin({ usuarios: usuarios$, roles: roles$, permisos: permisos$ }).subscribe(
      ({ usuarios, roles, permisos: permisosLista }) => {
        if (puedeUsuarios) {
          this.usuariosTotal.set(usuarios.length);
          this.usuariosActivos.set(usuarios.filter((usuario) => usuario.activo).length);
        }
        if (puedeRoles) {
          this.rolesTotal.set(roles.length);
        }
        if (puedePermisos) {
          this.permisosTotal.set(permisosLista.length);
        }
        this.cargando.set(false);
      },
    );
  }
}
