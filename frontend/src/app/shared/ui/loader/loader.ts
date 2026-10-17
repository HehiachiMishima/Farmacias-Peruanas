import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Carga } from '../../../core/services/carga';

@Component({
  selector: 'ui-loader',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (carga.visible()) {
      <div class="loader" role="status" aria-live="polite">
        <div class="loader__box">
          <span class="loader__ring"></span>
          <p class="loader__text">{{ carga.texto() }}</p>
        </div>
      </div>
    }
  `,
  styles: `
    :host { display: contents; }

    .loader {
      position: fixed;
      inset: 0;
      z-index: var(--z-loader);
      display: grid;
      place-items: center;
      background: color-mix(in srgb, var(--bg) 78%, transparent);
      backdrop-filter: blur(8px);
      animation: loader-fade var(--transition) both;
    }

    .loader__box {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-5);
      padding: var(--space-8) var(--space-10);
      animation: loader-pop var(--transition-slow) both;
    }

    .loader__ring {
      width: 96px;
      height: 96px;
      border-radius: 50%;
      border: 3px solid transparent;
      border-top-color: var(--brand);
      border-right-color: color-mix(in srgb, var(--brand) 35%, transparent);
      animation: ui-spin 0.9s linear infinite;
    }

    .loader__text {
      font-size: var(--fs-md);
      font-weight: 600;
      color: var(--text-2);
    }

    @keyframes loader-fade {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    @keyframes loader-pop {
      from { opacity: 0; transform: translateY(8px) scale(0.96); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }
  `,
})
export class Loader {
  protected readonly carga = inject(Carga);
}
