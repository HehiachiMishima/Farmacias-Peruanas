import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { PANELES, PanelDef } from '../../core/config/paneles';
import { Auth } from '../../core/services/auth';
import { Notificaciones } from '../../core/services/notificaciones';
import { TemaService } from '../../core/services/tema';
import { Icon } from '../../shared/ui/icon/icon';

@Component({
  selector: 'app-launcher',
  imports: [Icon],
  templateUrl: './launcher.html',
  styleUrl: './launcher.scss',
})
export class Launcher {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly notificaciones = inject(Notificaciones);
  readonly tema = inject(TemaService);

  readonly paneles = PANELES;
  readonly usuario = this.auth.usuario;
  readonly permisos = this.auth.permisos;

  readonly accesibles = computed(
    () => this.paneles.filter((panel) => this.permisos().includes(panel.permiso)).length,
  );

  readonly iniciales = computed(() => {
    const usuario = this.usuario();
    if (!usuario) {
      return '?';
    }
    return `${usuario.nombre?.[0] ?? ''}${usuario.apellido?.[0] ?? ''}`.toUpperCase() || '?';
  });

  accesible(panel: PanelDef): boolean {
    return this.permisos().includes(panel.permiso);
  }

  abrir(panel: PanelDef): void {
    if (this.accesible(panel)) {
      void this.router.navigateByUrl(panel.ruta);
      return;
    }
    this.notificaciones.error('Acceso denegado', `No tienes permiso para acceder a ${panel.nombre}.`);
  }

  alternarTema(evento: Event): void {
    this.tema.alternar(evento);
  }

  cerrarSesion(): void {
    this.auth.cerrarSesion();
  }
}
