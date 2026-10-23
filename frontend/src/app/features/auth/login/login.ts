import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { mensajeDeError } from '../../../core/errors/error.util';
import { Auth } from '../../../core/services/auth';
import { Carga } from '../../../core/services/carga';
import { Notificaciones } from '../../../core/services/notificaciones';
import { Icon } from '../../../shared/ui/icon/icon';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, Icon],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notificaciones = inject(Notificaciones);
  private readonly carga = inject(Carga);

  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly verContrasena = signal(false);

  /** Nodos (centros de distribución) dibujados sobre las rutas del fondo. */
  readonly nodos: ReadonlyArray<readonly [number, number]> = [
    [140, 362], [360, 380], [600, 268], [120, 236], [400, 200], [640, 330], [300, 525], [520, 440],
  ];

  /** Concepto de la plataforma: acróstico ATLAS, letra por letra. */
  readonly letras: ReadonlyArray<{ letra: string; palabra: string; descripcion: string; color: string; icono: string }> = [
    { letra: 'A', palabra: 'Análisis', descripcion: 'Indicadores, Fill Rate, incidencias y desempeño operativo.', color: '#0ea56b', icono: 'dashboard' },
    { letra: 'T', palabra: 'Trazabilidad', descripcion: 'Seguimiento de pedidos, rutas, procesos e incidencias.', color: '#10b981', icono: 'truck' },
    { letra: 'L', palabra: 'Logística', descripcion: 'Gestión de las herramientas y procesos de la operación.', color: '#34d399', icono: 'paquete' },
    { letra: 'A', palabra: 'Automatización', descripcion: 'Reducción de tareas manuales y consolidación de reportes.', color: '#059669', icono: 'automatizacion' },
    { letra: 'S', palabra: 'Sincronización', descripcion: 'Integración de información de diferentes sistemas y fuentes.', color: '#22c55e', icono: 'sincronizacion' },
  ];

  readonly formulario = this.fb.nonNullable.group({
    usuario: ['', [Validators.required]],
    contrasena: ['', [Validators.required]],
  });

  campoInvalido(campo: 'usuario' | 'contrasena'): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  toggleContrasena(): void {
    this.verContrasena.update((valor) => !valor);
  }

  enviar(): void {
    if (this.cargando()) {
      return;
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.cargando.set(true);
    this.error.set(null);
    this.carga.mostrar('Iniciando sesión…');

    const { usuario, contrasena } = this.formulario.getRawValue();

    this.auth.iniciarSesion({ usuario, contrasena }).subscribe({
      next: (datos) => {
        this.cargando.set(false);
        this.notificaciones.exito('Acceso concedido', `Hola, ${datos.usuario?.nombre ?? usuario}.`);
        const destino = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        void this.router.navigateByUrl(destino).then(() => {
          setTimeout(() => this.carga.ocultar(), 450);
        });
      },
      error: (err: unknown) => {
        this.cargando.set(false);
        this.carga.ocultar();
        this.error.set(mensajeDeError(err));
      },
    });
  }
}
