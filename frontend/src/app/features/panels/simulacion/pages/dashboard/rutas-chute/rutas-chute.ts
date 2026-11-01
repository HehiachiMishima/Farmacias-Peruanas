import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, finalize } from 'rxjs';
import { mensajeDeError } from '../../../../../../core/errors/error.util';
import { Pagina } from '../../../../../../core/models';
import { EmptyState } from '../../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../../shared/ui/paginador/paginador';
import { SelectBuscable } from '../../../../../../shared/ui/select-buscable/select-buscable';
import {
  ChuteComparativoSimulacion,
  ClusterComparativoSimulacion,
  LadoChute,
  ResumenRutasSimulacion,
  RutaSimulacion,
} from '../../../data/simulacion.model';
import { SimulacionApi } from '../../../data/simulacion.api';
import { compararNombre, formatearHoras, formatearNumero } from '../../../simulacion-formato';

type TipoFiltro = '' | 'B2C' | 'B2B';

interface Indicador {
  etiqueta: string;
  antes: string;
  despues: string;
  variacion: string;
  /** cambio: varió sin juicio de valor; mejora/empeora solo para el tiempo. */
  tono: 'igual' | 'cambio' | 'mejora' | 'empeora';
}

/** Indicador de conteo o volumen: la variación se muestra con signo («+3», «−2», «=»). */
function par(etiqueta: string, antes: number, despues: number, decimales: number): Indicador {
  const delta = Number(despues) - Number(antes);
  const igual = Math.abs(delta) < 0.005;
  return {
    etiqueta,
    antes: formatearNumero(antes, decimales),
    despues: formatearNumero(despues, decimales),
    variacion: igual
      ? '='
      : `${delta > 0 ? '+' : '−'}${formatearNumero(Math.abs(delta), decimales)}`,
    tono: igual ? 'igual' : 'cambio',
  };
}

/** Estado de un lado del chute (antes o después): su página, totales y carga. */
class LadoRutas {
  readonly resumen = signal<ResumenRutasSimulacion | null>(null);
  readonly datos = signal<Pagina<RutaSimulacion> | null>(null);
  readonly pagina = signal(1);
  readonly porPagina = signal(10);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  /** Descarta respuestas viejas cuando se filtra o pagina rápido. */
  solicitud = 0;

  constructor(readonly lado: LadoChute) {}

  reiniciar(): void {
    this.resumen.set(null);
    this.datos.set(null);
    this.error.set(null);
    this.pagina.set(1);
  }
}

/**
 * Modal ejecutivo de un chute: sus rutas al cargar el Excel frente a las que
 * tiene tras el balanceo, con búsqueda y filtros comunes a ambos lados.
 */
@Component({
  selector: 'app-simulacion-rutas-chute',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Modal, Icon, EmptyState, Paginador, SelectBuscable],
  templateUrl: './rutas-chute.html',
  styleUrl: './rutas-chute.scss',
})
export class SimulacionRutasChute {
  private readonly api = inject(SimulacionApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly abierto = input(false);
  readonly registroId = input<number | null>(null);
  readonly chutes = input<ChuteComparativoSimulacion[]>([]);
  readonly clusters = input<ClusterComparativoSimulacion[]>([]);
  readonly cerrar = output<void>();

  readonly formatearNumero = formatearNumero;
  readonly formatearHoras = formatearHoras;

  readonly chuteId = signal<number | null>(null);
  readonly texto = signal('');
  readonly tipo = signal<TipoFiltro>('');
  readonly clusterId = signal<number | null>(null);
  readonly soloCambios = signal(false);
  private readonly busqueda$ = new Subject<string>();

  readonly antes = new LadoRutas('ANTES');
  readonly despues = new LadoRutas('DESPUES');
  readonly lados = [this.antes, this.despues];

  readonly ordenados = computed(() =>
    this.chutes()
      .slice()
      .sort((a, b) => compararNombre(a.chute, b.chute)),
  );

  readonly opcionesChute = computed(() =>
    this.ordenados().map((chute) => ({
      id: chute.id,
      etiqueta:
        chute.clusters.length > 0 ? `${chute.chute} · ${chute.clusters.join(', ')}` : chute.chute,
    })),
  );

  readonly chute = computed(() => this.ordenados().find((c) => c.id === this.chuteId()) ?? null);
  readonly indice = computed(() => this.ordenados().findIndex((c) => c.id === this.chuteId()));

  /** Solo los clusters que el chute atiende antes o después. */
  readonly opcionesCluster = computed(() => {
    const nombres = this.chute()?.clusters ?? [];
    return this.clusters()
      .filter((cluster) => nombres.includes(cluster.cluster))
      .sort((a, b) => compararNombre(a.cluster, b.cluster))
      .map((cluster) => ({ id: cluster.id, etiqueta: cluster.cluster }));
  });

  readonly tieneFiltros = computed(
    () =>
      this.texto().trim().length > 0 ||
      this.tipo() !== '' ||
      this.clusterId() !== null ||
      this.soloCambios(),
  );

  /**
   * Flujo de rutas del chute: las que se quedaron, las que salieron y las que
   * llegaron. Las barras comparten escala para que antes y después se lean juntas.
   */
  readonly flujo = computed(() => {
    const chute = this.chute();
    if (!chute) {
      return null;
    }
    const antes = Number(chute.rutasOriginal);
    const despues = Number(chute.rutasBalanceado);
    const salieron = Number(chute.rutasSalieron);
    const llegaron = Number(chute.rutasLlegaron);
    const quedaron = Math.max(0, antes - salieron);
    const escala = Math.max(1, antes, despues);
    return {
      antes,
      despues,
      salieron,
      llegaron,
      quedaron,
      pctQuedaron: (quedaron / escala) * 100,
      pctSalieron: (salieron / escala) * 100,
      pctLlegaron: (llegaron / escala) * 100,
    };
  });

  /** Indicadores del chute en pares antes → después, con su variación. */
  readonly indicadores = computed<Indicador[]>(() => {
    const chute = this.chute();
    if (!chute) {
      return [];
    }
    const tiempo = Number(chute.tiempoBalanceado) - Number(chute.tiempoOriginal);
    return [
      par('Rutas', chute.rutasOriginal, chute.rutasBalanceado, 0),
      par('Bultos', chute.bultosOriginal, chute.bultosBalanceado, 2),
      par('Operarios', chute.operariosOriginal, chute.operariosBalanceado, 0),
      {
        etiqueta: 'Tiempo de cierre',
        antes: formatearHoras(chute.tiempoOriginal),
        despues: formatearHoras(chute.tiempoBalanceado),
        variacion:
          Math.abs(tiempo) < 0.005
            ? '='
            : `${tiempo > 0 ? '+' : '−'}${formatearHoras(Math.abs(tiempo))}`,
        // En el tiempo, bajar es la mejora que busca el balanceo.
        tono: Math.abs(tiempo) < 0.005 ? 'igual' : tiempo < 0 ? 'mejora' : 'empeora',
      },
    ];
  });

  constructor() {
    // Al abrir: chute con más cambios (o el primero), filtros limpios y ambos lados.
    effect(() => {
      if (this.abierto()) {
        untracked(() => this.iniciar());
      }
    });
    this.busqueda$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((texto) => {
      if (texto.trim() === this.texto().trim()) {
        return;
      }
      this.texto.set(texto);
      this.recargar();
    });
  }

  private iniciar(): void {
    const chutes = this.ordenados();
    const conCambios = chutes.reduce<ChuteComparativoSimulacion | null>((mejor, chute) => {
      const cambios = Number(chute.rutasSalieron) + Number(chute.rutasLlegaron);
      const mejores = mejor ? Number(mejor.rutasSalieron) + Number(mejor.rutasLlegaron) : 0;
      return cambios > mejores ? chute : mejor;
    }, null);
    this.chuteId.set((conCambios ?? chutes[0])?.id ?? null);
    this.reiniciarFiltros();
    this.recargar();
  }

  seleccionarChute(id: number | null): void {
    if (id === null || id === this.chuteId()) {
      return;
    }
    this.chuteId.set(id);
    // Los clusters dependen del chute: un filtro de cluster ajeno vaciaría la vista.
    this.clusterId.set(null);
    this.recargar();
  }

  /** Recorre los chutes en orden natural con las flechas. */
  moverChute(paso: number): void {
    const siguiente = this.ordenados()[this.indice() + paso];
    if (siguiente) {
      this.seleccionarChute(siguiente.id);
    }
  }

  buscar(texto: string): void {
    this.busqueda$.next(texto);
  }

  setTipo(valor: string): void {
    this.tipo.set(valor === 'B2C' || valor === 'B2B' ? valor : '');
    this.recargar();
  }

  setCluster(valor: number | null): void {
    this.clusterId.set(valor);
    this.recargar();
  }

  setSoloCambios(valor: boolean): void {
    this.soloCambios.set(valor);
    this.recargar();
  }

  limpiarFiltros(): void {
    this.reiniciarFiltros();
    this.recargar();
  }

  private reiniciarFiltros(): void {
    this.busqueda$.next('');
    this.texto.set('');
    this.tipo.set('');
    this.clusterId.set(null);
    this.soloCambios.set(false);
  }

  /** Cualquier cambio de chute o filtro vuelve ambos lados a la primera página. */
  private recargar(): void {
    for (const lado of this.lados) {
      lado.reiniciar();
      this.cargar(lado, 1);
    }
  }

  cambiarPagina(lado: LadoRutas, pagina: number): void {
    this.cargar(lado, pagina);
  }

  cambiarPorPagina(lado: LadoRutas, valor: number): void {
    lado.porPagina.set(valor);
    this.cargar(lado, 1);
  }

  cargar(lado: LadoRutas, pagina: number): void {
    const id = this.registroId();
    const chuteId = this.chuteId();
    if (!id || chuteId === null) {
      return;
    }
    lado.pagina.set(pagina);
    const solicitud = ++lado.solicitud;
    lado.cargando.set(true);
    lado.error.set(null);
    this.api
      .rutasDashboard(id, {
        pagina,
        tamano: lado.porPagina(),
        texto: this.texto().trim(),
        tipo: this.tipo(),
        chuteId,
        lado: lado.lado,
        clusterId: this.clusterId(),
        soloCambios: this.soloCambios(),
      })
      .pipe(
        finalize(() => {
          if (solicitud === lado.solicitud) {
            lado.cargando.set(false);
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (respuesta) => {
          if (solicitud === lado.solicitud) {
            lado.resumen.set(respuesta.resumen);
            lado.datos.set(respuesta.rutas);
          }
        },
        error: (err: unknown) => {
          if (solicitud === lado.solicitud) {
            lado.error.set(mensajeDeError(err));
          }
        },
      });
  }
}
