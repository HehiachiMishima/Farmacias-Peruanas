package oracle.admin.service;

import oracle.common.support.ActorActual;
import oracle.common.support.Trazas;

import java.time.LocalDateTime;
import java.util.List;
import oracle.common.exception.ConflictoException;
import oracle.common.exception.OperacionNoPermitidaException;
import oracle.common.exception.RecursoNoEncontradoException;
import oracle.admin.model.Rol;
import oracle.admin.model.Usuario;
import oracle.admin.model.UsuarioRol;
import oracle.admin.repository.RolRepository;
import oracle.admin.repository.UsuarioRepository;
import oracle.admin.repository.UsuarioRolRepository;
import oracle.admin.request.UsuarioRolAsignarRequest;
import oracle.common.response.ApiResponse;
import oracle.admin.response.RolResponse;
import oracle.admin.response.UsuarioResponse;
import oracle.admin.response.UsuarioRolResponse;
import oracle.common.security.AutorizacionCache;
import oracle.common.config.Transacciones;
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
    private final AutorizacionCache autorizacionCache;
    private final Transacciones transacciones;

    public UsuarioRolService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            UsuarioRolRepository usuarioRolRepository,
            AutorizacionCache autorizacionCache,
            Transacciones transacciones) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.autorizacionCache = autorizacionCache;
        this.transacciones = transacciones;
    }

    /** Asignar un rol al usuario (unico por par). */
    public Mono<ApiResponse<UsuarioRolResponse>> asignar(Long usuarioId, UsuarioRolAsignarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log,
                        "UsuarioRolService.asignar usuarioId=" + usuarioId + " rolId=" + peticion.rolId(),
                        () -> transacciones.ejecutar(() -> {
                            Usuario usuario = obtenerUsuario(usuarioId);
                            verificarNoProtegido(usuario);
                            Protecciones.verificarNoAutoGestion(usuarioId, actor);
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
                            autorizacionCache.invalidar(usuarioId);

                            return ApiResponse.ok("Rol asignado.", UsuarioRolResponse.of(asignacion));
                        })))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Quitar un rol al usuario. */
    public Mono<ApiResponse<Void>> quitar(Long usuarioId, Long rolId) {
        Long actor = ActorActual.id();
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log,
                        "UsuarioRolService.quitar usuarioId=" + usuarioId + " rolId=" + rolId,
                        () -> transacciones.ejecutar(() -> {
                            Usuario usuario = obtenerUsuario(usuarioId);
                            Protecciones.verificarNoAutoGestion(usuarioId, actor);
                            verificarNoProtegido(usuario);
                            if (!usuarioRolRepository.existsByUsuarioIdAndRolId(usuarioId, rolId)) {
                                throw new RecursoNoEncontradoException("El usuario '" + usuario.getUsuario()
                                        + "' no tiene el rol con id " + rolId + ".");
                            }
                            usuarioRolRepository.eliminarPar(usuarioId, rolId);
                            autorizacionCache.invalidar(usuarioId);
                            return null;
                        })))
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
