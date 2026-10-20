package oracle.admin.service;

import oracle.common.support.Trazas;

import java.util.List;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.admin.repository.ResolucionRepository;
import oracle.admin.repository.UsuarioRepository;
import oracle.common.response.ApiResponse;
import oracle.admin.response.PermisoEfectivoResponse;
import oracle.admin.response.PermisoFinalResponse;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Vista de resolucion de permisos: como se calcula lo efectivo/final. */
@Slf4j
@Service
public class ResolucionPermisosService {

    private final UsuarioRepository usuarioRepository;
    private final ResolucionRepository resolucionRepository;

    public ResolucionPermisosService(
            UsuarioRepository usuarioRepository,
            ResolucionRepository resolucionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.resolucionRepository = resolucionRepository;
    }

    public Mono<ApiResponse<List<PermisoEfectivoResponse>>> efectivos(Long usuarioId) {
        return Mono.fromCallable(() -> Trazas.medir(log, "ResolucionPermisosService.efectivos usuarioId=" + usuarioId, () -> {
                    validarUsuario(usuarioId);
                    return resolucionRepository.efectivos(usuarioId);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Permisos efectivos.", lista));
    }

    public Mono<ApiResponse<List<PermisoFinalResponse>>> finales(Long usuarioId) {
        return Mono.fromCallable(() -> Trazas.medir(log, "ResolucionPermisosService.finales usuarioId=" + usuarioId, () -> {
                    validarUsuario(usuarioId);
                    return resolucionRepository.finales(usuarioId);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Permisos finales.", lista));
    }

    public Mono<ApiResponse<List<PermisoFinalResponse>>> permitidos(Long usuarioId) {
        return Mono.fromCallable(() -> Trazas.medir(log, "ResolucionPermisosService.permitidos usuarioId=" + usuarioId, () -> {
                    validarUsuario(usuarioId);
                    return resolucionRepository.permitidos(usuarioId);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Permisos permitidos.", lista));
    }

    public Mono<ApiResponse<Boolean>> tienePermiso(Long usuarioId, Long permisoId) {
        return Mono.fromCallable(() -> Trazas.medir(log,
                        "ResolucionPermisosService.tienePermiso usuarioId=" + usuarioId + " permisoId=" + permisoId,
                        () -> {
                            validarUsuario(usuarioId);
                            return resolucionRepository.tienePermitido(usuarioId, permisoId);
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(tiene -> ApiResponse.ok("Consulta de permiso.", tiene));
    }

    private void validarUsuario(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("No existe el usuario con id " + id + ".");
        }
    }
}
