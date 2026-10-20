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
import oracle.admin.model.Permiso;
import oracle.admin.repository.PermisoRepository;
import oracle.admin.request.CambiarEstadoRequest;
import oracle.admin.request.PermisoActualizarRequest;
import oracle.admin.request.PermisoCrearRequest;
import oracle.admin.request.PermisoFiltroRequest;
import oracle.common.response.ApiResponse;
import oracle.admin.response.PermisoResponse;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * CRUD de permisos. Los permisos base (Protecciones.PERMISOS_BASE) estan
 * blindados: no se pueden modificar ni eliminar, exactamente igual que los
 * triggers trg_permisos_base_update / trg_permisos_base_delete de la BD.
 * Se valida aqui para responder 403 limpio antes de que MySQL de SQLSTATE 45000.
 */
@Slf4j
@Service
public class PermisoService {

    private final AutorizacionCache autorizacionCache;
    private final PermisoRepository permisoRepository;

    public PermisoService(PermisoRepository permisoRepository, AutorizacionCache autorizacionCache) {
        this.autorizacionCache = autorizacionCache;
        this.permisoRepository = permisoRepository;
    }

    public Mono<ApiResponse<List<PermisoResponse>>> listar() {
        return Mono.fromCallable(() -> Trazas.medir(log, "PermisoService.listar", () -> {
                        List<PermisoResponse> lista = permisoRepository.findAll(Listas.limitado()).stream()
                                .map(PermisoResponse::of).toList();
                        Listas.avisarSiCorto(log, lista.size(), "PermisoService.listar");
                        return lista;
                    }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Permisos listados.", lista));
    }

    public Mono<ApiResponse<List<PermisoResponse>>> buscar(PermisoFiltroRequest filtro) {
        return Mono.fromCallable(() -> Trazas.medir(log, "PermisoService.buscar", () ->
                        permisoRepository.buscarConFiltros(
                                        Filtros.normalizar(filtro.nombre()),
                                        Filtros.normalizar(filtro.descripcion()),
                                        filtro.activo())
                                .stream().map(PermisoResponse::of).toList()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Permisos encontrados.", lista));
    }

    public Mono<ApiResponse<PermisoResponse>> obtenerPorId(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "PermisoService.obtenerPorId id=" + id, () ->
                        ApiResponse.ok("Permiso encontrado.", PermisoResponse.of(obtenerPermiso(id)))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<PermisoResponse>> crear(PermisoCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "PermisoService.crear nombre=" + peticion.nombre(), () -> {
            if (permisoRepository.existsByNombre(peticion.nombre())) {
                throw new ConflictoException("Ya existe un permiso con el nombre '" + peticion.nombre() + "'.");
            }

            Permiso permiso = new Permiso();
            permiso.setNombre(peticion.nombre().trim());
            permiso.setDescripcion(peticion.descripcion());
            permiso.setActivo(peticion.activo() == null ? Boolean.TRUE : peticion.activo());
            Auditoria.alCrear(permiso, actor);
            permisoRepository.save(permiso);
            autorizacionCache.invalidarTodo();

            return ApiResponse.ok("Permiso creado.", PermisoResponse.of(permiso));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<PermisoResponse>> actualizar(Long id, PermisoActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "PermisoService.actualizar id=" + id, () -> {
            Permiso permiso = obtenerPermiso(id);
            boolean esBase = Protecciones.esPermisoBase(permiso.getNombre());

            boolean cambiaNombre = peticion.nombre() != null
                    && !peticion.nombre().trim().equals(permiso.getNombre());
            boolean cambiaDescripcion = peticion.descripcion() != null
                    && !peticion.descripcion().equals(permiso.getDescripcion());
            boolean cambiaActivo = peticion.activo() != null
                    && !peticion.activo().equals(permiso.getActivo());
            if (esBase && (cambiaNombre || cambiaDescripcion || cambiaActivo)) {
                throw new OperacionNoPermitidaException(
                        "No se puede modificar un permiso base. Es inmutable (nombre, descripcion y estado).");
            }

            if (cambiaNombre) {
                if (permisoRepository.existsByNombreAndIdNot(peticion.nombre().trim(), id)) {
                    throw new ConflictoException("Ya existe otro permiso con el nombre '" + peticion.nombre() + "'.");
                }
                permiso.setNombre(peticion.nombre().trim());
            }
            if (cambiaDescripcion) {
                permiso.setDescripcion(peticion.descripcion());
            }
            if (peticion.activo() != null) {
                permiso.setActivo(peticion.activo());
            }

            Auditoria.alActualizar(permiso, actor);
            permisoRepository.save(permiso);
            autorizacionCache.invalidarTodo();
            return ApiResponse.ok("Permiso actualizado.", PermisoResponse.of(permiso));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "PermisoService.eliminar id=" + id, () -> {
                    Permiso permiso = obtenerPermiso(id);
                    if (Protecciones.esPermisoBase(permiso.getNombre())) {
                        throw new OperacionNoPermitidaException(
                                "No se puede eliminar un permiso base. Es inmutable e intocable.");
                    }
                    permisoRepository.delete(permiso);
                    autorizacionCache.invalidarTodo();
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Permiso eliminado.", null)));
    }

    public Mono<ApiResponse<PermisoResponse>> cambiarActivo(Long id, CambiarEstadoRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "PermisoService.cambiarActivo id=" + id, () -> {
            Permiso permiso = obtenerPermiso(id);
            if (Protecciones.esPermisoBase(permiso.getNombre())) {
                throw new OperacionNoPermitidaException(
                        "No se puede activar ni desactivar un permiso base. Es inmutable.");
            }
            permiso.setActivo(peticion.activo());
            Auditoria.alActualizar(permiso, actor);
            permisoRepository.save(permiso);
            autorizacionCache.invalidarTodo();

            String mensaje = peticion.activo() ? "Permiso activado." : "Permiso desactivado.";
            return ApiResponse.ok(mensaje, PermisoResponse.of(permiso));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    private Permiso obtenerPermiso(Long id) {
        return permisoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el permiso con id " + id + "."));
    }
}
