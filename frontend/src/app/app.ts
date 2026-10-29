import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SesionSincronizador } from './core/services/sesion-sincronizador';
import { SesionVigilante } from './core/services/sesion-vigilante';
import { TemaService } from './core/services/tema';
import { Loader } from './shared/ui/loader/loader';
import { ToastHost } from './shared/ui/toast/toast-host';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, ToastHost, Loader],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly tema = inject(TemaService);
  private readonly vigilante = inject(SesionVigilante);
  private readonly sincronizador = inject(SesionSincronizador);
}
