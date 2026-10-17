import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { PANEL_ADMIN } from '../../../core/config/paneles';
import { Auth } from '../../../core/services/auth';
import { TemaService } from '../../../core/services/tema';
import { Icon } from '../../../shared/ui/icon/icon';

@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.scss',
})
export class AdminShell {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  readonly tema = inject(TemaService);

  readonly panel = PANEL_ADMIN;
  readonly usuario = this.auth.usuario;
  readonly menuAbierto = signal(this.pantallaGrande());

  readonly modulos = computed(() => {
    const permisos = this.auth.permisos();
    return this.panel.modulos.filter((modulo) => permisos.includes(modulo.permiso));
  });

  readonly iniciales = computed(() => {
    const usuario = this.usuario();
    if (!usuario) {
      return '?';
    }
    return `${usuario.nombre?.[0] ?? ''}${usuario.apellido?.[0] ?? ''}`.toUpperCase() || '?';
  });

  alternarMenu(): void {
    this.menuAbierto.update((valor) => !valor);
  }

  cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  @HostListener('document:keydown.escape')
  cerrarConEscape(): void {
    this.cerrarMenu();
  }

  private pantallaGrande(): boolean {
    return typeof window !== 'undefined' && window.matchMedia('(min-width: 1024px)').matches;
  }

  alternarTema(evento: Event): void {
    this.tema.alternar(evento);
  }

  irAPaneles(): void {
    void this.router.navigateByUrl('/');
  }

  cerrarSesion(): void {
    this.auth.cerrarSesion();
  }
}
