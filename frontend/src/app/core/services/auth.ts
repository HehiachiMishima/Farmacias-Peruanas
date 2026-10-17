import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, AuthResponse, LoginRequest } from '../models';
import { Carga } from './carga';
import { SesionStore } from './sesion';

@Service()
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sesionStore = inject(SesionStore);
  private readonly carga = inject(Carga);
  private readonly base = environment.apiUrl.replace(/\/+$/, '');

  readonly sesion = this.sesionStore.sesion;
  readonly permisos = this.sesionStore.permisos;
  readonly usuario = this.sesionStore.usuario;
  readonly autenticado = this.sesionStore.autenticado;

  iniciarSesion(credenciales: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.base}/auth/login`, credenciales)
      .pipe(
        map((respuesta) => respuesta.datos),
        tap((datos) =>
          this.sesionStore.guardar({
            token: datos.token,
            tipo: datos.tipo,
            expiraEn: datos.expiraEn,
            usuario: datos.usuario,
            permisos: datos.permisos,
          }),
        ),
      );
  }

  cerrarSesion(redirigir = true): void {
    this.sesionStore.limpiar();
    if (!redirigir) {
      return;
    }
    this.carga.mostrar('Cerrando sesión…');
    void this.router.navigate(['/login']).then(() => {
      setTimeout(() => this.carga.ocultar(), 450);
    });
  }

  tienePermiso(permiso: string): boolean {
    return this.sesionStore.tienePermiso(permiso);
  }
}
