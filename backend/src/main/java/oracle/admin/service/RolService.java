package oracle.admin.service;

import oracle.common.security.AutorizacionCache;
import oracle.common.support.ActorActual;
import oracle.common.support.Auditoria;
import oracle.common.support.Filtros;
import oracle.common.support.Listas;
import oracle.common.support.Trazas;

import java.util.List;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.OperacionNoPermitidaException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.admin.model.Rol;
import oracle.admin.repository.RolPermisoRepository;
import oracle.admin.repository.RolRepository;
import oracle.admin.request.CambiarEstadoRequest;
import oracle.admin.request.RolActualizarRequest;
import oracle.admin.request.RolCrearRequest;
import oracle.admin.request.RolFiltroRequest;
import oracle.common.response.ApiResponse;
import oracle.admin.response.PermisoResponse;
import oracle.admin.response.RolResponse;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** CRUD de roles con filtros LIKE y protecciones del trigger de ADMINISTRADOR. */
@Slf4j
@Service
public class RolService {

    private final AutorizacionCache autorizacionCache;
    private final RolRepository rolRepository;
    private final RolPermisoRepository rolPermisoRepository;

    public RolService(RolRepository rolRepository, RolPermisoRepository rolPermisoRepository, AutorizacionCache autorizacionCache) {
        this.autorizacionCache = autorizacionCache;
        this.rolRepository = rolRepository;
        this.rolPermisoRepository = rolPermisoRepository;
    }

    public Mono<ApiResponse<List<RolResponse>>> listar() {
        return Mono.fromCallable(() -> Trazas.medir(log, "RolService.listar", () -> {
                        List<RolResponse> lista = rolRepository.findAll(Listas.limitado()).stream()
                                .map(RolResponse::of).toList();
                        Listas.avisarSiCorto(log, lista.size(), "RolService.listar");
                        return lista;
                    }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Roles listados.", lista));
    }

    public Mono<ApiResponse<List<RolResponse>>> buscar(RolFiltroRequest filtro) {
        return Mono.fromCallable(() -> Trazas.medir(log, "RolService.buscar", () ->
                        rolRepository.buscarConFiltros(
                                        Filtros.normalizar(filtro.nombre()),
                                        Filtros.normalizar(filtro.descripcion()),
                                        filtro.activo())
                                .stream().map(RolResponse::of).toList()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Roles encontrados.", lista));
    }

    public Mono<ApiResponse<RolResponse>> obtenerPorId(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "RolService.obtenerPorId id=" + id, () ->
                        ApiResponse.ok("Rol encontrado.", RolResponse.of(obtenerRol(id)))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<RolResponse>> crear(RolCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "RolService.crear nombre=" + peticion.nombre(), () -> {
            if (rolRepository.existsByNombre(peticion.nombre())) {
                throw new ConflictoException("Ya existe un rol con el nombre '" + peticion.nombre() + "'.");
            }

            Rol rol = new Rol();
            rol.setNombre(peticion.nombre().trim());
            rol.setDescripcion(peticion.descripcion());
            rol.setActivo(peticion.activo() == null ? Boolean.TRUE : peticion.activo());
            Auditoria.alCrear(rol, actor);
            rolRepository.save(rol);
            autorizacionCache.invalidarTodo();

            return ApiResponse.ok("Rol creado.", RolResponse.of(rol));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<RolResponse>> actualizar(Long id, RolActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "RolService.actualizar id=" + id, () -> {
            Rol rol = obtenerRol(id);
            verificarNoAdministrador(rol);

            if (peticion.nombre() != null && !peticion.nombre().trim().equals(rol.getNombre())) {
                if (rolRepository.existsByNombreAndIdNot(peticion.nombre().trim(), id)) {
                    throw new ConflictoException("Ya existe otro rol con el nombre '" + peticion.nombre() + "'.");
                }
                rol.setNombre(peticion.nombre().trim());
            }
            if (peticion.descripcion() != null) {
                rol.setDescripcion(peticion.descripcion());
            }
            if (peticion.activo() != null) {
                rol.setActivo(peticion.activo());
            }

            Auditoria.alActualizar(rol, actor);
            rolRepository.save(rol);
            autorizacionCache.invalidarTodo();
            return ApiResponse.ok("Rol actualizado.", RolResponse.of(rol));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "RolService.eliminar id=" + id, () -> {
                    Rol rol = obtenerRol(id);
                    verificarNoAdministrador(rol);
                    rolRepository.delete(rol);
                    autorizacionCache.invalidarTodo();
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Rol eliminado.", null)));
    }

    public Mono<ApiResponse<RolResponse>> cambiarActivo(Long id, CambiarEstadoRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "RolService.cambiarActivo id=" + id, () -> {
            Rol rol = obtenerRol(id);
            verificarNoAdministrador(rol);
            rol.setActivo(peticion.activo());
            Auditoria.alActualizar(rol, actor);
            rolRepository.save(rol);
            autorizacionCache.invalidarTodo();

            String mensaje = peticion.activo() ? "Rol activado." : "Rol desactivado.";
            return ApiResponse.ok(mensaje, RolResponse.of(rol));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<List<PermisoResponse>>> permisosDeRol(Long rolId) {
        return Mono.fromCallable(() -> Trazas.medir(log, "RolService.permisosDeRol rolId=" + rolId, () -> {
                    obtenerRol(rolId);
                    return rolPermisoRepository.permisosDeRol(rolId).stream().map(PermisoResponse::of).toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Permisos del rol.", lista));
    }

    private Rol obtenerRol(Long id) {
        return rolRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol con id " + id + "."));
    }

    private void verificarNoAdministrador(Rol rol) {
        if (Protecciones.esRolAdministrador(rol.getNombre())) {
            throw new OperacionNoPermitidaException(
                    "No se puede modificar ni eliminar el rol ADMINISTRADOR. Solo se le pueden agregar permisos.");
        }
    }
}
