import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActorLabelPipe } from '../../../../../../shared/pipes/actor-label.pipe';
import { EmptyState } from '../../../../../../shared/ui/empty-state/empty-state';
import { Modal } from '../../../../../../shared/ui/modal/modal';
import { formatearNumero } from '../../../simulacion-formato';
import { GrupoHistorial } from '../configuracion.logica';

/** Modal con los cambios del registro: un bloque por balanceo, del más reciente al más antiguo. */
@Component({
  selector: 'app-simulacion-historial-cambios',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, ActorLabelPipe, EmptyState, Modal],
  templateUrl: './historial-cambios.html',
  styleUrl: './historial-cambios.scss',
})
export class SimulacionHistorialCambios {
  readonly abierto = input(false);
  readonly cargando = input(false);
  readonly grupos = input.required<GrupoHistorial[]>();
  readonly cerrar = output<void>();

  readonly formatearNumero = formatearNumero;
}
