import { AfterViewInit, ChangeDetectionStrategy, Component, effect, ElementRef, input, OnDestroy, viewChild } from '@angular/core';
import { COMPOSITE_SHADER, MARCH_SHADER, VERTEX_SHADER } from './light-pillar.shaders';

/**
 * ui-light-pillar — columna de luz interactiva en WebGL2.
 *
 * Port a Angular (sin React, sin dependencias de terceros) del componente
 * Light Pillar de React Bits: https://reactbits.dev/backgrounds/light-pillar
 * MIT Commons Clause v1.0 — Copyright (c) 2026 David Haya.
 *
 * Uso: colocarlo como capa de fondo dentro de un contenedor con
 * `position: relative`. El host se posiciona a pantalla completa sobre su
 * contenedor, no captura puntero (`pointer-events: none`) y se mezcla con el
 * fondo a través de `mixBlendMode`. Todo lo visual se expone como inputs.
 *
 * El motor (marcha + composición) es imperativo: no necesita change
 * detection, solo monta al entrar en pantalla y libera todo al salir.
 */

type UniformMap = Record<string, WebGLUniformLocation | null>;

interface MarchUniforms {
  uTime: WebGLUniformLocation | null;
  uResolution: WebGLUniformLocation | null;
  uTarget: WebGLUniformLocation | null;
  uRot: WebGLUniformLocation | null;
  uPillarRot: WebGLUniformLocation | null;
  uWave: WebGLUniformLocation | null;
  uShift: WebGLUniformLocation | null;
  uPillarWidth: WebGLUniformLocation | null;
  uPillarHeight: WebGLUniformLocation | null;
  uDetail: WebGLUniformLocation | null;
  uTopColor: WebGLUniformLocation | null;
  uBottomColor: WebGLUniformLocation | null;
  uGlowAmount: WebGLUniformLocation | null;
  uWidthNorm: WebGLUniformLocation | null;
}

interface CompositeUniforms {
  uField: WebGLUniformLocation | null;
  uFieldSize: WebGLUniformLocation | null;
  uView: WebGLUniformLocation | null;
  uAspect: WebGLUniformLocation | null;
  uPillarRot: WebGLUniformLocation | null;
  uShift: WebGLUniformLocation | null;
  uIntensity: WebGLUniformLocation | null;
  uNoiseIntensity: WebGLUniformLocation | null;
  uLightMode: WebGLUniformLocation | null;
  uNeutral: WebGLUniformLocation | null;
  uBloom: WebGLUniformLocation | null;
  uFade: WebGLUniformLocation | null;
  uReveal: WebGLUniformLocation | null;
  uFlare: WebGLUniformLocation | null;
  uPulses: WebGLUniformLocation | null;
}

interface Program<T> {
  program: WebGLProgram;
  uniforms: T;
}

interface Settings {
  topColor: string;
  bottomColor: string;
  intensity: number;
  rotationSpeed: number;
  flow: number;
  interactive: boolean;
  clickPulse: boolean;
  glowAmount: number;
  bloom: number;
  pillarWidth: number;
  pillarHeight: number;
  pillarRotation: number;
  twist: number;
  detail: number;
  noiseIntensity: number;
  fade: number;
  intro: boolean;
  scale: number;
  lightMode: boolean;
  paused: boolean;
}

interface Pulse {
  axis: number;
  age: number;
}

const QUALITY: Record<string, number> = { low: 0.35, medium: 0.5, high: 0.6 };
const INTRO_SECONDS = 2.1;
const PULSE_LIFE = 1.4;
const MIN_SCALE = 0.25;

const clamp = (value: number, min: number, max: number): number => Math.min(max, Math.max(min, value));
const easeOut = (t: number): number => 1 - Math.pow(1 - t, 3);
const ease = (t: number): number => t * t * t * (t * (t * 6 - 15) + 10);

const compile = (gl: WebGL2RenderingContext, type: number, source: string): WebGLShader | null => {
  const shader = gl.createShader(type);
  if (!shader) return null;
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (gl.getShaderParameter(shader, gl.COMPILE_STATUS)) return shader;
  gl.deleteShader(shader);
  return null;
};

const link = <T>(gl: WebGL2RenderingContext, fragmentSource: string): Program<T> | null => {
  const vertex = compile(gl, gl.VERTEX_SHADER, VERTEX_SHADER);
  const fragment = compile(gl, gl.FRAGMENT_SHADER, fragmentSource);
  const program = gl.createProgram();
  if (!vertex || !fragment || !program) return null;
  gl.attachShader(program, vertex);
  gl.attachShader(program, fragment);
  gl.bindAttribLocation(program, 0, 'aPosition');
  gl.linkProgram(program);
  gl.deleteShader(vertex);
  gl.deleteShader(fragment);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    gl.deleteProgram(program);
    return null;
  }
  const uniforms: UniformMap = {};
  const count = gl.getProgramParameter(program, gl.ACTIVE_UNIFORMS);
  for (let i = 0; i < count; i++) {
    const info = gl.getActiveUniform(program, i);
    if (info) uniforms[info.name.replace(/\[0\]$/, '')] = gl.getUniformLocation(program, info.name);
  }
  return { program, uniforms: uniforms as unknown as T };
};

@Component({
  selector: 'ui-light-pillar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[style.mixBlendMode]': 'mixBlendMode()',
  },
  template: `
    <div class="light-pillar" #container>
      <canvas class="light-pillar__canvas" #canvas aria-hidden="true"></canvas>
    </div>
  `,
  styles: `
    :host {
      position: absolute;
      inset: 0;
      display: block;
      overflow: hidden;
      pointer-events: none;
    }
    .light-pillar {
      position: relative;
      width: 100%;
      height: 100%;
      overflow: hidden;
    }
    .light-pillar__canvas {
      position: absolute;
      inset: 0;
      display: block;
      width: 100%;
      height: 100%;
    }
  `,
})
export class LightPillar implements AfterViewInit, OnDestroy {
  /** Color superior del gradiente de la columna. */
  readonly topColor = input<string>('#5227FF');
  /** Color inferior del gradiente de la columna. */
  readonly bottomColor = input<string>('#FF9FFC');
  readonly intensity = input<number>(1.0);
  readonly rotationSpeed = input<number>(0.3);
  readonly flow = input<number>(1);
  /** La columna se inclina hacia el cursor. */
  readonly interactive = input<boolean>(true);
  /** Un destello de luz recorre la columna en cada clic. */
  readonly clickPulse = input<boolean>(true);
  readonly glowAmount = input<number>(0.002);
  readonly bloom = input<number>(0.5);
  readonly pillarWidth = input<number>(3.0);
  readonly pillarHeight = input<number>(0.4);
  readonly pillarRotation = input<number>(25);
  readonly twist = input<number>(0.4);
  readonly detail = input<number>(4);
  readonly noiseIntensity = input<number>(0.5);
  readonly fade = input<number>(0);
  readonly intro = input<boolean>(true);
  /** `screen` para fondos oscuros, `multiply`/`normal` para fondos claros. */
  readonly mixBlendMode = input<string>('screen');
  readonly quality = input<'low' | 'medium' | 'high'>('high');
  /** Escala de render explícita; si no se pasa se deriva de `quality`. */
  readonly resolution = input<number>();
  /** Modo pensado para fondos claros. */
  readonly lightMode = input<boolean>(false);
  readonly paused = input<boolean>(false);

  private readonly containerRef = viewChild.required<ElementRef<HTMLDivElement>>('container');
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  private settings!: Settings;
  private wake: (() => void) | null = null;
  private cleanup: (() => void) | null = null;

  constructor() {
    effect(() => {
      this.settings = this.snapshot();
      this.wake?.();
    });
  }

  ngAfterViewInit(): void {
    this.settings = this.snapshot();
    this.mount();
  }

  ngOnDestroy(): void {
    this.destroy();
  }

  private snapshot(): Settings {
    return {
      topColor: String(this.topColor()),
      bottomColor: String(this.bottomColor()),
      intensity: Math.max(0, this.intensity()),
      rotationSpeed: this.rotationSpeed(),
      flow: this.flow(),
      interactive: this.interactive(),
      clickPulse: this.clickPulse(),
      glowAmount: Math.max(0, this.glowAmount()),
      bloom: Math.max(0, this.bloom()),
      pillarWidth: Math.max(0.05, this.pillarWidth()),
      pillarHeight: this.pillarHeight(),
      pillarRotation: this.pillarRotation(),
      twist: this.twist(),
      detail: Math.round(clamp(this.detail(), 1, 6)),
      noiseIntensity: Math.max(0, this.noiseIntensity()),
      fade: clamp(this.fade(), 0, 1),
      intro: this.intro(),
      scale: clamp(this.resolution() ?? QUALITY[this.quality()] ?? QUALITY['high'], MIN_SCALE, 2),
      lightMode: this.lightMode(),
      paused: this.paused(),
    };
  }

  private destroy(): void {
    this.cleanup?.();
    this.cleanup = null;
    this.wake = null;
  }

  private mount(): void {
    if (this.cleanup) this.destroy();

    const container = this.containerRef().nativeElement;
    const canvas = this.canvasRef().nativeElement;
    const gl = canvas.getContext('webgl2', {
      alpha: true,
      premultipliedAlpha: true,
      antialias: false,
      depth: false,
      stencil: false,
      powerPreference: 'high-performance',
    });
    if (!gl) return;

    const floatTargets = !!gl.getExtension('EXT_color_buffer_float');
    const marchProgram = link<MarchUniforms>(gl, MARCH_SHADER);
    const compositeProgram = link<CompositeUniforms>(gl, COMPOSITE_SHADER);
    if (!marchProgram || !compositeProgram) return;

    const onContextLost = (event: Event) => {
      event.preventDefault();
    };
    const onContextRestored = () => {
      this.destroy();
      this.mount();
    };
    canvas.addEventListener('webglcontextlost', onContextLost);
    canvas.addEventListener('webglcontextrestored', onContextRestored);

    const buffer = gl.createBuffer();
    const vao = gl.createVertexArray();
    gl.bindVertexArray(vao);
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);

    let texture: WebGLTexture | null = null;
    const framebuffer = gl.createFramebuffer();
    const target = { width: 0, height: 0 };
    const sizeTarget = (width: number, height: number) => {
      if (target.width === width && target.height === height) return;
      target.width = width;
      target.height = height;
      if (texture) gl.deleteTexture(texture);
      texture = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, texture);
      const levels = Math.floor(Math.log2(Math.max(width, height))) + 1;
      gl.texStorage2D(gl.TEXTURE_2D, levels, floatTargets ? gl.RGBA16F : gl.RGBA8, width, height);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
    };

    const probe = document.createElement('canvas').getContext('2d', { willReadFrequently: true });
    const colorCache = new Map<string, number[]>();
    const toRgb = (value: string): number[] => {
      const cached = colorCache.get(value);
      if (cached) return cached;
      let rgb = [1, 1, 1];
      if (probe) {
        probe.clearRect(0, 0, 1, 1);
        probe.fillStyle = value;
        probe.fillRect(0, 0, 1, 1);
        const [r, g, b] = probe.getImageData(0, 0, 1, 1).data;
        rgb = [r / 255, g / 255, b / 255];
      }
      colorCache.set(value, rgb);
      return rgb;
    };

    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    const view = { width: 1, height: 1, ratio: 1 };
    const pointer = { x: 0, y: 0, inside: false };
    const pulses: Pulse[] = [];
    const pulseData = new Float32Array(12);
    const state = {
      clock: 0,
      intro: this.settings.intro && !reduce ? 0 : 1,
      touch: 0,
      spin: 0,
      lean: 0,
      shift: 0,
      governor: 1,
      slow: 0,
      quick: 0,
    };
    let raf = 0;
    let last = 0;
    let visible = true;

    const axisAt = (x: number, y: number, angle: number): number => {
      const aspect = view.width / view.height;
      const px = ((x / view.width) * 2 - 1) * aspect - state.shift;
      const py = 1 - (y / view.height) * 2;
      return Math.sin(angle) * px + Math.cos(angle) * py;
    };

    const render = (): void => {
      const s = this.settings;
      const scale = Math.max(MIN_SCALE, s.scale * state.governor);
      const fieldWidth = Math.max(1, Math.round(view.width * scale));
      const fieldHeight = Math.max(1, Math.round(view.height * scale));
      sizeTarget(fieldWidth, fieldHeight);

      const grow = ease(state.intro);
      const rise = easeOut(state.intro);
      const tilt = ((s.pillarRotation + state.lean) * Math.PI) / 180;
      const angle = state.clock * 0.3 + state.spin;
      const width = s.pillarWidth * (0.3 + 0.7 * grow);

      gl.bindVertexArray(vao);
      const m = marchProgram.uniforms;
      gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
      gl.viewport(0, 0, target.width, target.height);
      gl.useProgram(marchProgram.program);
      gl.uniform1f(m.uTime, state.clock * s.flow);
      gl.uniform2f(m.uResolution, view.width, view.height);
      gl.uniform2f(m.uTarget, target.width, target.height);
      gl.uniform2f(m.uRot, Math.cos(angle), Math.sin(angle));
      gl.uniform2f(m.uPillarRot, Math.cos(tilt), Math.sin(tilt));
      gl.uniform2f(m.uWave, Math.sin(s.twist), Math.cos(s.twist));
      gl.uniform1f(m.uShift, state.shift);
      gl.uniform1f(m.uPillarWidth, width);
      gl.uniform1f(m.uPillarHeight, s.pillarHeight);
      gl.uniform1i(m.uDetail, s.detail);
      gl.uniform3fv(m.uTopColor, toRgb(s.topColor));
      gl.uniform3fv(m.uBottomColor, toRgb(s.bottomColor));
      gl.uniform1f(m.uGlowAmount, s.glowAmount);
      gl.uniform1f(m.uWidthNorm, width / 3);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      if (s.bloom > 0) gl.generateMipmap(gl.TEXTURE_2D);

      pulseData.fill(0);
      pulses.forEach((pulse, index) => {
        pulseData.set([pulse.axis + pulse.age * 1.7, Math.exp(-pulse.age / (PULSE_LIFE * 0.45)), 0, 0], index * 4);
      });

      const c = compositeProgram.uniforms;
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.useProgram(compositeProgram.program);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.uniform1i(c.uField, 0);
      gl.uniform2f(c.uFieldSize, target.width, target.height);
      gl.uniform2f(c.uView, canvas.width, canvas.height);
      gl.uniform2f(c.uAspect, view.width / view.height, 1);
      gl.uniform2f(c.uPillarRot, Math.cos(tilt), Math.sin(tilt));
      gl.uniform1f(c.uShift, state.shift);
      gl.uniform1f(c.uIntensity, s.intensity * (0.15 + 0.85 * rise) * (1 + 0.12 * state.touch));
      gl.uniform1f(c.uNoiseIntensity, s.noiseIntensity);
      gl.uniform1f(c.uLightMode, s.lightMode ? 1 : 0);
      const chroma = (rgb: number[]): number => Math.max(...rgb) - Math.min(...rgb);
      const vivid = Math.max(chroma(toRgb(s.topColor)), chroma(toRgb(s.bottomColor)));
      gl.uniform1f(c.uNeutral, 1 - clamp((vivid - 0.08) / 0.12, 0, 1));
      gl.uniform1f(c.uBloom, s.bloom);
      gl.uniform1f(c.uFade, s.fade);
      const span = view.width / view.height + 1.2;
      gl.uniform1f(c.uReveal, state.intro >= 1 ? 1e4 : -span + rise * (span * 2 + 1.2));
      gl.uniform1f(c.uFlare, state.intro >= 1 ? 0 : (1 - rise) * 1.3 * Math.min(1, state.intro * 8));
      gl.uniform4fv(c.uPulses, pulseData);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    };

    const tune = (dt: number): void => {
      if (dt > 1 / 40) {
        state.slow += 1;
        state.quick = 0;
      } else if (dt < 1 / 55) {
        state.quick += 1;
        state.slow = 0;
      }
      if (state.slow > 20) {
        state.governor = Math.max(0.5, state.governor * 0.85);
        state.slow = 0;
      } else if (state.quick > 180 && state.governor < 1) {
        state.governor = Math.min(1, state.governor * 1.1);
        state.quick = 0;
      }
    };

    const frame = (now: number): void => {
      raf = 0;
      if (!visible || document.hidden) return;
      const s = this.settings;
      const dt = last ? Math.min(0.05, (now - last) / 1000) : 1 / 60;
      if (last) tune((now - last) / 1000);
      last = now;
      const running = !s.paused && !reduce && s.rotationSpeed !== 0;
      if (running) state.clock += dt * 0.96 * s.rotationSpeed;
      if (!s.intro || reduce) state.intro = 1;
      else if (state.intro < 1) state.intro = Math.min(1, state.intro + dt / INTRO_SECONDS);

      const engaged = s.interactive && pointer.inside && !reduce;
      const aim = engaged ? pointer.x / view.width - 0.5 : 0;
      const follow = 1 - Math.exp(-dt * 2.4);
      state.spin += (aim * 1.4 - state.spin) * follow;
      state.lean += (aim * 9 - state.lean) * follow;
      state.shift += (aim * 0.35 * (view.width / view.height) - state.shift) * follow;
      state.touch += ((engaged ? 1 : 0) - state.touch) * (1 - Math.exp(-dt * 3));

      for (let i = pulses.length - 1; i >= 0; i--) {
        pulses[i].age += dt;
        if (pulses[i].age > PULSE_LIFE * 2.2) pulses.splice(i, 1);
      }

      render();

      const settling =
        state.intro < 1 ||
        pulses.length > 0 ||
        Math.abs(aim * 1.4 - state.spin) > 0.0005 ||
        Math.abs((engaged ? 1 : 0) - state.touch) > 0.002;
      if (running || settling) raf = requestAnimationFrame(frame);
      else last = 0;
    };

    const wake = (): void => {
      if (!raf && visible && !document.hidden) raf = requestAnimationFrame(frame);
    };

    const resize = (): void => {
      const width = Math.max(1, container.clientWidth);
      const height = Math.max(1, container.clientHeight);
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      view.width = width;
      view.height = height;
      view.ratio = ratio;
      canvas.width = Math.max(1, Math.round(width * ratio));
      canvas.height = Math.max(1, Math.round(height * ratio));
      if (!raf) render();
      wake();
    };

    const locate = (event: PointerEvent): { x: number; y: number; inside: boolean } => {
      const rect = container.getBoundingClientRect();
      const x = event.clientX - rect.left;
      const y = event.clientY - rect.top;
      return { x, y, inside: x >= 0 && y >= 0 && x <= rect.width && y <= rect.height };
    };

    const onPointerMove = (event: PointerEvent): void => {
      const point = locate(event);
      pointer.x = point.x;
      pointer.y = point.y;
      pointer.inside = point.inside;
      wake();
    };

    const onPointerDown = (event: PointerEvent): void => {
      const s = this.settings;
      if (!s.clickPulse || reduce) return;
      const point = locate(event);
      if (!point.inside) return;
      const tilt = ((s.pillarRotation + state.lean) * Math.PI) / 180;
      pulses.push({ axis: axisAt(point.x, point.y, tilt), age: 0 });
      if (pulses.length > 3) pulses.shift();
      wake();
    };

    const onPointerLeave = (): void => {
      pointer.inside = false;
      wake();
    };

    const onVisibility = (): void => {
      last = 0;
      wake();
    };

    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(container);
    const intersection = new IntersectionObserver(entries => {
      visible = entries.some(entry => entry.isIntersecting);
      if (visible) {
        last = 0;
        wake();
      }
    });
    intersection.observe(container);
    window.addEventListener('pointermove', onPointerMove, { passive: true });
    window.addEventListener('pointerdown', onPointerDown, { passive: true });
    document.documentElement.addEventListener('pointerleave', onPointerLeave);
    document.addEventListener('visibilitychange', onVisibility);
    resize();

    this.wake = () => {
      if (!raf) render();
      wake();
    };

    this.cleanup = () => {
      cancelAnimationFrame(raf);
      canvas.removeEventListener('webglcontextlost', onContextLost);
      canvas.removeEventListener('webglcontextrestored', onContextRestored);
      resizeObserver.disconnect();
      intersection.disconnect();
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerdown', onPointerDown);
      document.documentElement.removeEventListener('pointerleave', onPointerLeave);
      document.removeEventListener('visibilitychange', onVisibility);
      if (texture) gl.deleteTexture(texture);
      gl.deleteFramebuffer(framebuffer);
      gl.deleteProgram(marchProgram.program);
      gl.deleteProgram(compositeProgram.program);
      gl.deleteBuffer(buffer);
      gl.deleteVertexArray(vao);
      if (!gl.isContextLost()) gl.getExtension('WEBGL_lose_context')?.loseContext();
    };
  }
}
