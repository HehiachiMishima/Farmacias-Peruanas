import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { mensajeDeError } from '../../../core/errors/error.util';
import { Auth } from '../../../core/services/auth';
import { Carga } from '../../../core/services/carga';
import { Notificaciones } from '../../../core/services/notificaciones';
import { Icon } from '../../../shared/ui/icon/icon';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  private readonly hero = viewChild.required<ElementRef<HTMLElement>>('hero');

  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly verContrasena = signal(false);

  readonly titulo = ['A', 'T', 'L', 'A', 'S'] as const;

  /** Concepto de la plataforma: acróstico ATLAS. */
  readonly concepto = ['Análisis', 'Trazabilidad', 'Logística', 'Automatización', 'Sincronización'] as const;

  /** Capas de la operación: pines alrededor de la escena. */
  readonly capas: ReadonlyArray<{ icono: string; texto: string; x: string; y: string }> = [
    { icono: 'almacen', texto: 'Almacenes', x: '10%', y: '12%' },
    { icono: 'truck', texto: 'Distribución', x: '90%', y: '10%' },
    { icono: 'farmacia', texto: 'Farmacias', x: '6%', y: '60%' },
    { icono: 'dashboard', texto: 'Datos y analítica', x: '94%', y: '58%' },
  ];

  /** Capas de la plataforma mostradas en la barra inferior del hero. */
  readonly pie: ReadonlyArray<{ texto: string; icono: string }> = [
    { texto: 'Cadena de suministro', icono: 'almacen' },
    { texto: 'Integración de sistemas', icono: 'engranaje' },
    { texto: 'Datos y analítica', icono: 'base_datos' },
    { texto: 'Procesos logísticos', icono: 'nodos' },
    { texto: 'Distribución', icono: 'truck' },
    { texto: 'Farmacias', icono: 'farmacia' },
    { texto: 'Indicadores de operación', icono: 'dashboard' },
  ];

  /** Red de rutas sobre el mapa del Perú (viewBox 260×360). */
  readonly rutas = [
    'M40 118 Q46 142 62 168',
    'M62 168 Q74 202 100 246',
    'M100 246 Q112 262 120 276',
    'M120 276 Q150 300 170 322',
    'M100 246 Q132 244 160 250',
    'M160 250 Q198 256 230 268',
    'M160 250 Q170 198 170 150',
    'M170 150 Q166 106 165 74',
  ] as const;

  /** Nodos de la red: [x, y, radio]. */
  readonly nodos: ReadonlyArray<readonly [number, number, number]> = [
    [40, 118, 3.4], [62, 168, 3.4], [100, 246, 5.6], [120, 276, 3.4], [170, 322, 3.4],
    [160, 250, 4.4], [230, 268, 3.4], [170, 150, 3.4], [165, 74, 3.4],
  ];

  readonly formulario = this.fb.nonNullable.group({
    usuario: ['', [Validators.required]],
    contrasena: ['', [Validators.required]],
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => destroyRef.onDestroy(this.activarParallax(this.hero().nativeElement)));
  }

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
    this.carga.mostrar('Ingresando a Atlas', 'Preparando tus paneles de operación');

    const { usuario, contrasena } = this.formulario.getRawValue();

    this.auth.iniciarSesion({ usuario, contrasena }).subscribe({
      next: (datos) => {
        this.cargando.set(false);
        this.notificaciones.exito('Acceso concedido', `Hola, ${datos.usuario?.nombre ?? usuario}.`);
        const destino = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        void this.router.navigateByUrl(destino).then(() => this.carga.ocultar());
      },
      error: (err: unknown) => {
        this.cargando.set(false);
        this.carga.ocultar(true);
        this.error.set(mensajeDeError(err));
      },
    });
  }

  /**
   * Parallax sutil de la escena siguiendo el puntero: publica --mx / --my
   * (-1…1) en el hero, una vez por frame. Solo con puntero fino y sin
   * preferencia de movimiento reducido. Devuelve la función de limpieza.
   */
  private activarParallax(hero: HTMLElement): () => void {
    const apto = window.matchMedia('(pointer: fine) and (prefers-reduced-motion: no-preference)');
    if (!apto.matches) {
      return () => undefined;
    }
    let frame = 0;
    const mover = (evento: PointerEvent): void => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const caja = hero.getBoundingClientRect();
        const x = ((evento.clientX - caja.left) / caja.width) * 2 - 1;
        const y = ((evento.clientY - caja.top) / caja.height) * 2 - 1;
        hero.style.setProperty('--mx', x.toFixed(3));
        hero.style.setProperty('--my', y.toFixed(3));
      });
    };
    const salir = (): void => {
      cancelAnimationFrame(frame);
      hero.style.setProperty('--mx', '0');
      hero.style.setProperty('--my', '0');
    };
    hero.addEventListener('pointermove', mover, { passive: true });
    hero.addEventListener('pointerleave', salir);
    return () => {
      cancelAnimationFrame(frame);
      hero.removeEventListener('pointermove', mover);
      hero.removeEventListener('pointerleave', salir);
    };
  }
}
