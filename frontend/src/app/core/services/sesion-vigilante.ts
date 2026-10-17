import { Service, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { CONFIG } from '../config/configuracion';
import { Sesion } from '../models';
import { Notificaciones } from './notificaciones';
import { SesionStore } from './sesion';

/**
 * Vigila el ciclo de vida de la sesión: al llegar la hora de vencimiento
 * del token cierra la sesión, avisa y redirige al login, aunque el usuario
 * no haya hecho ninguna petición (el backend solo responde 401 al usarlo).
 */
@Service()
export class SesionVigilante {
  private readonly sesionStore = inject(SesionStore);
  private readonly notificaciones = inject(Notificaciones);
  private readonly router = inject(Router);
  private temporizador?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => this.programar(this.sesionStore.sesion()));
  }

  private programar(sesion: Sesion | null): void {
    this.cancelar();
    if (!sesion?.expiraEn) {
      return;
    }

    const restante = new Date(sesion.expiraEn).getTime() - Date.now() - CONFIG.sesion.margenExpiracionMs;
    if (restante <= 0) {
      this.expirar();
      return;
    }
    this.temporizador = setTimeout(() => this.expirar(), restante);
  }

  private cancelar(): void {
    if (this.temporizador !== undefined) {
      clearTimeout(this.temporizador);
      this.temporizador = undefined;
    }
  }

  private expirar(): void {
    if (this.sesionStore.sesion() === null) {
      return;
    }
    this.sesionStore.limpiar();
    this.notificaciones.advertencia('Sesión expirada', 'Vuelve a iniciar sesión para continuar.');
    if (!this.router.url.startsWith(CONFIG.rutas.login)) {
      void this.router.navigate([CONFIG.rutas.login], {
        queryParams: { returnUrl: this.router.url },
      });
    }
  }
}
