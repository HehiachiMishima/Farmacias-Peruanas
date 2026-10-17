package oracle.services;

import java.time.LocalDateTime;
import java.util.List;
import oracle.exception.OperacionNoPermitidaException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.model.Permiso;
import oracle.model.Usuario;
import oracle.model.UsuarioPermiso;
import oracle.repository.PermisoRepository;
import oracle.repository.UsuarioPermisoRepository;
import oracle.repository.UsuarioRepository;
import oracle.request.UsuarioPermisoOverrideRequest;
import oracle.response.ApiResponse;
import oracle.response.PermisoResponse;
import oracle.response.UsuarioPermisoResponse;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Overrides individuales (usuarios_permisos): otorgado=true es un grant y
 * false un deny. Con MIN(otorgado) en la vista, el deny siempre gana.
 */
@Slf4j
@Service
public class UsuarioPermisoService {

    private final UsuarioRepository usuarioRepository;
    private final PermisoRepository permisoRepository;
    private final UsuarioPermisoRepository usuarioPermisoRepository;

    public UsuarioPermisoService(
            UsuarioRepository usuarioRepository,
            PermisoRepository permisoRepository,
            UsuarioPermisoRepository usuarioPermisoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.permisoRepository = permisoRepository;
        this.usuarioPermisoRepository = usuarioPermisoRepository;
    }

    /** Crear o actualizar el override del permiso para el usuario. */
    public Mono<ApiResponse<UsuarioPermisoResponse>> otorgar(Long usuarioId, UsuarioPermisoOverrideRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log,
                "UsuarioPermisoService.otorgar usuarioId=" + usuarioId + " permisoId=" + peticion.permisoId(),
                () -> {
                    Usuario usuario = obtenerUsuario(usuarioId);
                    if (Protecciones.esUsuarioProtegido(usuario.getUsuario())) {
                        throw new OperacionNoPermitidaException(
                                "El usuario '" + usuario.getUsuario() + "' es el usuario principal protegido: no se pueden conceder ni quitar permisos individuales.");
                    }
                    Permiso permiso = permisoRepository.findById(peticion.permisoId())
                            .orElseThrow(() -> new RecursoNoEncontradoException(
                                    "No existe el permiso con id " + peticion.permisoId() + "."));
                    boolean otorgado = peticion.otorgado();

                    UsuarioPermiso override = usuarioPermisoRepository
                            .findByUsuarioIdAndPermisoId(usuarioId, peticion.permisoId())
                            .orElse(null);

                    String mensaje;
                    if (override == null) {
                        override = new UsuarioPermiso();
                        override.setUsuario(usuario);
                        override.setPermiso(permiso);
                        override.setOtorgado(otorgado);
                        override.setCreadoPor(actor);
                        override.setCreadoEn(LocalDateTime.now());
                        override.setActualizadoPor(actor);
                        override.setActualizadoEn(LocalDateTime.now());
                        mensaje = otorgado ? "Permiso otorgado al usuario." : "Permiso denegado al usuario.";
                    } else {
                        override.setOtorgado(otorgado);
                        override.setActualizadoPor(actor);
                        override.setActualizadoEn(LocalDateTime.now());
                        mensaje = otorgado
                                ? "Override actualizado a grant (otorgado=TRUE)."
                                : "Override actualizado a deny (otorgado=FALSE).";
                    }
                    usuarioPermisoRepository.save(override);

                    return ApiResponse.ok(mensaje, UsuarioPermisoResponse.of(override));
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Eliminar el override y volver al calculo por rol. */
    public Mono<ApiResponse<Void>> eliminar(Long usuarioId, Long permisoId) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log,
                "UsuarioPermisoService.eliminar usuarioId=" + usuarioId + " permisoId=" + permisoId,
                () -> {
                    Usuario usuario = obtenerUsuario(usuarioId);
                    if (Protecciones.esUsuarioProtegido(usuario.getUsuario())) {
                        throw new OperacionNoPermitidaException(
                                "El usuario '" + usuario.getUsuario() + "' es el usuario principal protegido: no se pueden conceder ni quitar permisos individuales.");
                    }
                    if (!usuarioPermisoRepository.existsByUsuarioIdAndPermisoId(usuarioId, permisoId)) {
                        throw new RecursoNoEncontradoException(
                                "El usuario no tiene un override para el permiso con id " + permisoId + ".");
                    }
                    usuarioPermisoRepository.eliminarPar(usuarioId, permisoId);
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Override eliminado.", null)));
    }

    /** Overrides del usuario (grant y deny). */
    public Mono<ApiResponse<List<UsuarioPermisoResponse>>> listarPorUsuario(Long usuarioId) {
        return Mono.fromCallable(() -> Trazas.medir(log,
                "UsuarioPermisoService.listarPorUsuario usuarioId=" + usuarioId, () -> {
                    obtenerUsuario(usuarioId);
                    return usuarioPermisoRepository.findByUsuarioId(usuarioId).stream()
                            .map(UsuarioPermisoResponse::of)
                            .toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Overrides del usuario.", lista));
    }

    /** Solo los overrides deny (otorgado=FALSE). */
    public Mono<ApiResponse<List<UsuarioPermisoResponse>>> listarDenegados(Long usuarioId) {
        return Mono.fromCallable(() -> Trazas.medir(log,
                "UsuarioPermisoService.listarDenegados usuarioId=" + usuarioId, () -> {
                    obtenerUsuario(usuarioId);
                    return usuarioPermisoRepository.findByUsuarioIdAndOtorgado(usuarioId, Boolean.FALSE).stream()
                            .map(UsuarioPermisoResponse::of)
                            .toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Overrides denegados del usuario.", lista));
    }

    /** Permisos (entidad) sobre los que el usuario tiene un override. */
    public Mono<ApiResponse<List<PermisoResponse>>> permisosConOverride(Long usuarioId) {
        return Mono.fromCallable(() -> Trazas.medir(log,
                "UsuarioPermisoService.permisosConOverride usuarioId=" + usuarioId, () -> {
                    obtenerUsuario(usuarioId);
                    return usuarioPermisoRepository.findByUsuarioId(usuarioId).stream()
                            .map(up -> PermisoResponse.of(up.getPermiso()))
                            .toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Permisos con override del usuario.", lista));
    }

    private Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + id + "."));
    }
}
