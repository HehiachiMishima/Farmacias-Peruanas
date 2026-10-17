package oracle.services;

import java.time.LocalDateTime;
import java.util.List;
import oracle.exception.ConflictoException;
import oracle.exception.OperacionNoPermitidaException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.model.Rol;
import oracle.model.Usuario;
import oracle.model.UsuarioRol;
import oracle.repository.RolRepository;
import oracle.repository.UsuarioRepository;
import oracle.repository.UsuarioRolRepository;
import oracle.request.UsuarioRolAsignarRequest;
import oracle.response.ApiResponse;
import oracle.response.RolResponse;
import oracle.response.UsuarioResponse;
import oracle.response.UsuarioRolResponse;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Asignacion usuario-rol (usuarios_roles): dar y quitar roles. */
@Slf4j
@Service
public class UsuarioRolService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioRolRepository usuarioRolRepository;

    public UsuarioRolService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            UsuarioRolRepository usuarioRolRepository) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.usuarioRolRepository = usuarioRolRepository;
    }

    /** Asignar un rol al usuario (unico por par). */
    public Mono<ApiResponse<UsuarioRolResponse>> asignar(Long usuarioId, UsuarioRolAsignarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log,
                        "UsuarioRolService.asignar usuarioId=" + usuarioId + " rolId=" + peticion.rolId(),
                        () -> {
                            Usuario usuario = obtenerUsuario(usuarioId);
                            verificarNoProtegido(usuario);
                            Rol rol = rolRepository.findById(peticion.rolId())
                                    .orElseThrow(() -> new RecursoNoEncontradoException(
                                            "No existe el rol con id " + peticion.rolId() + "."));

                            if (usuarioRolRepository.existsByUsuarioIdAndRolId(usuarioId, peticion.rolId())) {
                                throw new ConflictoException("El usuario '" + usuario.getUsuario()
                                        + "' ya tiene el rol '" + rol.getNombre() + "'.");
                            }

                            UsuarioRol asignacion = new UsuarioRol();
                            asignacion.setUsuario(usuario);
                            asignacion.setRol(rol);
                            asignacion.setCreadoPor(actor);
                            asignacion.setCreadoEn(LocalDateTime.now());
                            usuarioRolRepository.save(asignacion);

                            return ApiResponse.ok("Rol asignado.", UsuarioRolResponse.of(asignacion));
                        }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Quitar un rol al usuario. */
    public Mono<ApiResponse<Void>> quitar(Long usuarioId, Long rolId) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log,
                        "UsuarioRolService.quitar usuarioId=" + usuarioId + " rolId=" + rolId,
                        () -> {
                            Usuario usuario = obtenerUsuario(usuarioId);
                            verificarNoProtegido(usuario);
                            if (!usuarioRolRepository.existsByUsuarioIdAndRolId(usuarioId, rolId)) {
                                throw new RecursoNoEncontradoException("El usuario '" + usuario.getUsuario()
                                        + "' no tiene el rol con id " + rolId + ".");
                            }
                            usuarioRolRepository.eliminarPar(usuarioId, rolId);
                            return null;
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Rol quitado.", null)));
    }

    /** Roles del usuario. */
    public Mono<ApiResponse<List<RolResponse>>> listarPorUsuario(Long usuarioId) {
        return Mono.fromCallable(() -> Trazas.medir(log,
                        "UsuarioRolService.listarPorUsuario usuarioId=" + usuarioId, () -> {
                            obtenerUsuario(usuarioId);
                            return usuarioRolRepository.rolesDeUsuario(usuarioId).stream()
                                    .map(RolResponse::of)
                                    .toList();
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Roles del usuario.", lista));
    }

    /** Usuarios que tienen un rol. */
    public Mono<ApiResponse<List<UsuarioResponse>>> listarPorRol(Long rolId) {
        return Mono.fromCallable(() -> Trazas.medir(log, "UsuarioRolService.listarPorRol rolId=" + rolId, () -> {
                    if (!rolRepository.existsById(rolId)) {
                        throw new RecursoNoEncontradoException("No existe el rol con id " + rolId + ".");
                    }
                    return usuarioRolRepository.usuariosDeRol(rolId).stream()
                            .map(UsuarioResponse::of)
                            .toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Usuarios con el rol.", lista));
    }

    private Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + id + "."));
    }

    private void verificarNoProtegido(Usuario usuario) {
        if (Protecciones.esUsuarioProtegido(usuario.getUsuario())) {
            throw new OperacionNoPermitidaException(
                    "El usuario '" + usuario.getUsuario() + "' esta protegido: no se puede modificar su rol.");
        }
    }
}
