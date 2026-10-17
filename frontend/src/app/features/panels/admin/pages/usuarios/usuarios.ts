import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, finalize } from 'rxjs';
import { esAccesoDenegado, mensajeDeError } from '../../../../../core/errors/error.util';
import { PERMISOS } from '../../../../../core/config/permisos';
import { Auth } from '../../../../../core/services/auth';
import { DirectorioUsuarios } from '../../../../../core/services/directorio-usuarios';
import { Notificaciones } from '../../../../../core/services/notificaciones';
import { PermisosApi } from '../../../../../core/services/permisos.api';
import { RolesApi } from '../../../../../core/services/roles.api';
import { UsuariosApi } from '../../../../../core/services/usuarios.api';
import { Permiso, Rol, Usuario } from '../../../../../core/models';

interface ResumenUsuario {
  roles: string[];
  permisos: string[];
}
import { HasPermiso } from '../../../../../shared/directives/has-permiso.directive';
import { ColumnasRedimensionables } from '../../../../../shared/directives/columnas-redimensionables.directive';
import { EmptyState } from '../../../../../shared/ui/empty-state/empty-state';
import { Icon } from '../../../../../shared/ui/icon/icon';
import { Modal } from '../../../../../shared/ui/modal/modal';
import { Paginador } from '../../../../../shared/ui/paginador/paginador';
import { ActorLabelPipe } from '../../../../../shared/pipes/actor-label.pipe';

@Component({
  selector: 'app-admin-usuarios',
  imports: [ReactiveFormsModule, DatePipe, ActorLabelPipe, Icon, Modal, EmptyState, HasPermiso, ColumnasRedimensionables, Paginador],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
})
export class Usuarios {
  private readonly fb = inject(FormBuilder);
  private readonly usuariosApi = inject(UsuariosApi);
  private readonly rolesApi = inject(RolesApi);
  private readonly permisosApi = inject(PermisosApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly auth = inject(Auth);
  private readonly directorio = inject(DirectorioUsuarios);

  readonly permisos = PERMISOS;
  readonly permisosSesion = this.auth.permisos;

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly usuarios = signal<Usuario[]>([]);
  readonly busqueda = signal('');

  readonly filtrados = computed(() => {
    const termino = this.busqueda().trim().toLowerCase();
    const lista = this.usuarios();
    if (!termino) {
      return lista;
    }
    return lista.filter((usuario) =>
      [usuario.usuario, usuario.nombre, usuario.apellido, usuario.email]
        .filter(Boolean)
        .some((campo) => campo.toLowerCase().includes(termino)),
    );
  });

  readonly pagina = signal(1);
  readonly porPagina = signal(10);

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.filtrados().length / this.porPagina())),
  );

  readonly paginaActual = computed(() =>
    Math.min(Math.max(1, this.pagina()), this.totalPaginas()),
  );

  readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.porPagina();
    return this.filtrados().slice(inicio, inicio + this.porPagina());
  });

  buscar(valor: string): void {
    this.busqueda.set(valor);
    this.pagina.set(1);
  }

  cambiarPorPagina(valor: number): void {
    this.porPagina.set(valor);
    this.pagina.set(1);
  }

  /** Resumen por usuario (roles asignados y permisos efectivos) para las columnas Rol/Permisos. */
  readonly cargandoResumen = signal(false);
  readonly resumen = signal<Map<number, ResumenUsuario>>(new Map());

  readonly formAbierto = signal(false);
  readonly editando = signal<Usuario | null>(null);
  readonly guardando = signal(false);

  readonly formulario = this.fb.nonNullable.group({
    usuario: ['', [Validators.required, Validators.maxLength(50)]],
    contrasena: [''],
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    apellido: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
    telefono: ['', [Validators.maxLength(20)]],
    activo: [true],
  });

  readonly eliminarAbierto = signal(false);
  readonly aEliminar = signal<Usuario | null>(null);
  readonly eliminando = signal(false);

  readonly rolesAbierto = signal(false);
  readonly usuarioRoles = signal<Usuario | null>(null);
  readonly cargandoRoles = signal(false);
  readonly catalogoRoles = signal<Rol[]>([]);
  readonly rolesAsignados = signal<Set<number>>(new Set());
  readonly cambiandoRol = signal<number | null>(null);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.usuariosApi
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (lista) => {
          this.usuarios.set(lista);
          this.directorio.establecer(lista);
          this.cargarResumen(lista);
        },
        error: (err: unknown) => this.error.set(mensajeDeError(err)),
      });
  }

  private cargarResumen(lista: Usuario[]): void {
    if (lista.length === 0) {
      this.resumen.set(new Map());
      return;
    }
    this.cargandoResumen.set(true);
    forkJoin({
      catalogo: this.permisosApi.listar(),
      porUsuario: forkJoin(
        lista.map((usuario) =>
          forkJoin({
            roles: this.usuariosApi.roles(usuario.id),
            finales: this.usuariosApi.resolucionFinales(usuario.id),
          }),
        ),
      ),
    })
      .pipe(finalize(() => this.cargandoResumen.set(false)))
      .subscribe({
        next: ({ catalogo, porUsuario }) => {
          const nombrePorId = new Map(catalogo.map((permiso) => [permiso.id, permiso.nombre]));
          const mapa = new Map<number, ResumenUsuario>();
          lista.forEach((usuario, indice) => {
            const info = porUsuario[indice];
            mapa.set(usuario.id, {
              roles: info.roles.map((rol) => rol.nombre),
              permisos: info.finales
                .filter((fila) => fila.permitido)
                .map((fila) => nombrePorId.get(fila.permisoId) ?? `#${fila.permisoId}`),
            });
          });
          this.resumen.set(mapa);
        },
        error: () => this.resumen.set(new Map()),
      });
  }

  tieneResumen(id: number): boolean {
    return this.resumen().has(id);
  }

  rolesDe(id: number): string[] {
    return this.resumen().get(id)?.roles ?? [];
  }

  permisosDe(id: number): string[] {
    return this.resumen().get(id)?.permisos ?? [];
  }

  iniciales(usuario: Usuario): string {
    return `${usuario.nombre?.[0] ?? ''}${usuario.apellido?.[0] ?? ''}`.toUpperCase() || '?';
  }

  campoInvalido(campo: 'usuario' | 'contrasena' | 'nombre' | 'apellido' | 'email' | 'telefono'): boolean {
    const control = this.formulario.controls[campo];
    return control.touched && control.invalid;
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({ usuario: '', contrasena: '', nombre: '', apellido: '', email: '', telefono: '', activo: true });
    this.formulario.controls.contrasena.setValidators([Validators.required, Validators.minLength(6)]);
    this.formulario.controls.contrasena.updateValueAndValidity();
    this.formAbierto.set(true);
  }

  abrirEditar(usuario: Usuario): void {
    this.editando.set(usuario);
    this.formulario.reset({
      usuario: usuario.usuario,
      contrasena: '',
      nombre: usuario.nombre,
      apellido: usuario.apellido,
      email: usuario.email,
      telefono: usuario.telefono ?? '',
      activo: usuario.activo,
    });
    this.formulario.controls.contrasena.setValidators([Validators.minLength(6)]);
    this.formulario.controls.contrasena.updateValueAndValidity();
    this.formAbierto.set(true);
  }

  cerrarForm(): void {
    this.formAbierto.set(false);
    this.editando.set(null);
  }

  guardar(): void {
    if (this.guardando()) {
      return;
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    const valores = this.formulario.getRawValue();
    const editando = this.editando();
    this.guardando.set(true);

    const peticion = editando
      ? this.usuariosApi
          .actualizar(editando.id, {
            usuario: valores.usuario,
            nombre: valores.nombre,
            apellido: valores.apellido,
            email: valores.email,
            telefono: valores.telefono || null,
            activo: valores.activo,
            ...(valores.contrasena ? { contrasena: valores.contrasena } : {}),
          })
          .pipe(finalize(() => this.guardando.set(false)))
      : this.usuariosApi
          .crear({
            usuario: valores.usuario,
            contrasena: valores.contrasena,
            nombre: valores.nombre,
            apellido: valores.apellido,
            email: valores.email,
            telefono: valores.telefono || null,
            activo: valores.activo,
          })
          .pipe(finalize(() => this.guardando.set(false)));

    peticion.subscribe({
      next: (usuario) => {
        this.notificaciones.exito('Guardado', `Usuario ${usuario.usuario} ${editando ? 'actualizado' : 'creado'}.`);
        this.cerrarForm();
        this.cargar();
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo guardar', mensajeDeError(err)),
    });
  }

  alternarActivo(usuario: Usuario): void {
    this.usuariosApi.cambiarActivo(usuario.id, !usuario.activo).subscribe({
      next: (actualizado) => {
        this.usuarios.update((lista) =>
          lista.map((item) => (item.id === actualizado.id ? actualizado : item)),
        );
        this.notificaciones.info(
          usuario.activo ? 'Usuario desactivado' : 'Usuario activado',
          usuario.usuario,
        );
      },
      error: (err: unknown) => this.notificaciones.error('Error', mensajeDeError(err)),
    });
  }

  confirmarEliminar(usuario: Usuario): void {
    this.aEliminar.set(usuario);
    this.eliminarAbierto.set(true);
  }

  eliminar(): void {
    const usuario = this.aEliminar();
    if (!usuario || this.eliminando()) {
      return;
    }
    this.eliminando.set(true);
    this.usuariosApi
      .eliminar(usuario.id)
      .pipe(finalize(() => this.eliminando.set(false)))
      .subscribe({
        next: () => {
          this.usuarios.update((lista) => lista.filter((item) => item.id !== usuario.id));
          this.notificaciones.exito('Eliminado', `Usuario ${usuario.usuario} eliminado.`);
          this.eliminarAbierto.set(false);
          this.aEliminar.set(null);
        },
        error: (err: unknown) => this.notificaciones.error('No se pudo eliminar', mensajeDeError(err)),
      });
  }

  abrirRoles(usuario: Usuario): void {
    this.usuarioRoles.set(usuario);
    this.rolesAbierto.set(true);
    this.cargandoRoles.set(true);
    forkJoin({
      catalogo: this.rolesApi.listar(),
      asignados: this.usuariosApi.roles(usuario.id),
    })
      .pipe(finalize(() => this.cargandoRoles.set(false)))
      .subscribe({
        next: ({ catalogo, asignados }) => {
          this.catalogoRoles.set(catalogo);
          this.rolesAsignados.set(new Set(asignados.map((rol) => rol.id)));
        },
        error: (err: unknown) => this.notificaciones.error('No se pudieron cargar los roles', mensajeDeError(err)),
      });
  }

  estaAsignado(rolId: number): boolean {
    return this.rolesAsignados().has(rolId);
  }

  alternarRol(rol: Rol): void {
    const usuario = this.usuarioRoles();
    if (!usuario || this.cambiandoRol()) {
      return;
    }
    this.cambiandoRol.set(rol.id);
    const asignado = this.estaAsignado(rol.id);
    const peticion = asignado
      ? this.usuariosApi.quitarRol(usuario.id, rol.id)
      : this.usuariosApi.asignarRol(usuario.id, rol.id);

    peticion.pipe(finalize(() => this.cambiandoRol.set(null))).subscribe({
      next: () => {
        this.rolesAsignados.update((set) => {
          const copia = new Set(set);
          if (asignado) {
            copia.delete(rol.id);
          } else {
            copia.add(rol.id);
          }
          return copia;
        });
        this.notificaciones.info(asignado ? 'Rol quitado' : 'Rol asignado', rol.nombre);
      },
      error: (err: unknown) => this.notificaciones.error('No se pudo actualizar el rol', mensajeDeError(err)),
    });
  }

  // --- Overrides de permisos por usuario (grant/deny) ---

  readonly permisosAbierto = signal(false);
  readonly usuarioPermisos = signal<Usuario | null>(null);
  readonly cargandoPermisos = signal(false);
  readonly catalogoPermisos = signal<Permiso[]>([]);
  readonly filtroPermisos = signal('');
  readonly cambiandoPermiso = signal<number | null>(null);

  private readonly overrides = signal<Map<number, boolean>>(new Map());
  private readonly otorgaRol = signal<Set<number>>(new Set());

  readonly permisosFiltrados = computed(() => {
    const termino = this.filtroPermisos().trim().toLowerCase();
    const lista = this.catalogoPermisos();
    if (!termino) {
      return lista;
    }
    return lista.filter((permiso) =>
      [permiso.nombre, permiso.descripcion ?? ''].some((campo) => campo.toLowerCase().includes(termino)),
    );
  });

  readonly totalEfectivos = computed(
    () => this.catalogoPermisos().filter((permiso) => this.permitidoDe(permiso.id)).length,
  );

  readonly totalConcedidos = computed(
    () => [...this.overrides().values()].filter((otorgado) => otorgado).length,
  );

  readonly totalDenegados = computed(
    () => [...this.overrides().values()].filter((otorgado) => !otorgado).length,
  );

  permiteOverride(): boolean {
    return this.permisosSesion().includes(PERMISOS.ASIGNAR_PERMISOS);
  }

  overrideDe(permisoId: number): boolean | undefined {
    return this.overrides().get(permisoId);
  }

  permitidoDe(permisoId: number): boolean {
    const override = this.overrides().get(permisoId);
    return override ?? this.otorgaRol().has(permisoId);
  }

  origenDe(permisoId: number): 'concedido' | 'denegado' | 'heredado' | 'sin' {
    const override = this.overrides().get(permisoId);
    if (override === true) {
      return 'concedido';
    }
    if (override === false) {
      return 'denegado';
    }
    return this.otorgaRol().has(permisoId) ? 'heredado' : 'sin';
  }

  abrirPermisos(usuario: Usuario): void {
    this.usuarioPermisos.set(usuario);
    this.filtroPermisos.set('');
    this.permisosAbierto.set(true);
    this.cargandoPermisos.set(true);
    forkJoin({
      catalogo: this.permisosApi.listar(),
      overrides: this.usuariosApi.permisos(usuario.id),
      efectivos: this.usuariosApi.resolucionEfectivos(usuario.id),
    })
      .pipe(finalize(() => this.cargandoPermisos.set(false)))
      .subscribe({
        next: ({ catalogo, overrides, efectivos }) => {
          this.catalogoPermisos.set(catalogo);
          this.overrides.set(new Map(overrides.map((item) => [item.permisoId, item.otorgado])));
          this.otorgaRol.set(
            new Set(
              efectivos
                .filter((fila) => fila.origen === 'ROL' && fila.otorgado)
                .map((fila) => fila.permisoId),
            ),
          );
        },
        error: (err: unknown) =>
          this.notificaciones.error('No se pudieron cargar los permisos', mensajeDeError(err)),
      });
  }

  conceder(permiso: Permiso): void {
    this.aplicarOverride(permiso, true);
  }

  denegar(permiso: Permiso): void {
    this.aplicarOverride(permiso, false);
  }

  heredar(permiso: Permiso): void {
    const usuario = this.usuarioPermisos();
    if (!usuario || this.cambiandoPermiso() !== null || !this.overrides().has(permiso.id)) {
      return;
    }
    this.cambiandoPermiso.set(permiso.id);
    this.usuariosApi
      .quitarPermiso(usuario.id, permiso.id)
      .pipe(finalize(() => this.cambiandoPermiso.set(null)))
      .subscribe({
        next: () => {
          this.overrides.update((mapa) => {
            const copia = new Map(mapa);
            copia.delete(permiso.id);
            return copia;
          });
          this.notificaciones.info('Permiso heredado del rol', permiso.nombre);
        },
        error: (err: unknown) => {
          if (!esAccesoDenegado(err)) {
            this.notificaciones.error('No se pudo actualizar', mensajeDeError(err));
          }
        },
      });
  }

  private aplicarOverride(permiso: Permiso, otorgado: boolean): void {
    const usuario = this.usuarioPermisos();
    if (!usuario || this.cambiandoPermiso() !== null || this.overrides().get(permiso.id) === otorgado) {
      return;
    }
    this.cambiandoPermiso.set(permiso.id);
    this.usuariosApi
      .otorgarPermiso(usuario.id, { permisoId: permiso.id, otorgado })
      .pipe(finalize(() => this.cambiandoPermiso.set(null)))
      .subscribe({
        next: () => {
          this.overrides.update((mapa) => new Map(mapa).set(permiso.id, otorgado));
          this.notificaciones.info(
            otorgado ? 'Permiso concedido' : 'Permiso denegado',
            permiso.nombre,
          );
        },
        error: (err: unknown) => {
          if (!esAccesoDenegado(err)) {
            this.notificaciones.error('No se pudo actualizar', mensajeDeError(err));
          }
        },
      });
  }
}
