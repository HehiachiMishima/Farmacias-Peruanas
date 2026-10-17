package oracle.services;

import java.util.List;
import oracle.exception.ConflictoException;
import oracle.exception.OperacionNoPermitidaException;
import oracle.exception.RecursoNoEncontradoException;
import oracle.model.Usuario;
import oracle.repository.UsuarioRepository;
import oracle.repository.UsuarioRolRepository;
import oracle.request.CambiarEstadoRequest;
import oracle.request.UsuarioActualizarRequest;
import oracle.request.UsuarioCrearRequest;
import oracle.request.UsuarioFiltroRequest;
import oracle.response.ApiResponse;
import oracle.response.RolResponse;
import oracle.response.UsuarioResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * CRUD de usuarios con filtros LIKE. JPA es bloqueante, asi que toda
 * operacion se ejecuta en un hilo de boundedElastic.
 */
@Slf4j
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UsuarioService(UsuarioRepository usuarioRepository, UsuarioRolRepository usuarioRolRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
    }

    public Mono<ApiResponse<List<UsuarioResponse>>> listar() {
        return Mono.fromCallable(() -> Trazas.medir(log, "UsuarioService.listar", () ->
                        usuarioRepository.findAll().stream().map(UsuarioResponse::of).toList()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Usuarios listados.", lista));
    }

    public Mono<ApiResponse<List<UsuarioResponse>>> buscar(UsuarioFiltroRequest filtro) {
        return Mono.fromCallable(() -> Trazas.medir(log, "UsuarioService.buscar", () ->
                        usuarioRepository.buscarConFiltros(
                                        Filtros.normalizar(filtro.usuario()),
                                        Filtros.normalizar(filtro.nombre()),
                                        Filtros.normalizar(filtro.apellido()),
                                        Filtros.normalizar(filtro.email()),
                                        filtro.activo())
                                .stream().map(UsuarioResponse::of).toList()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Usuarios encontrados.", lista));
    }

    public Mono<ApiResponse<UsuarioResponse>> obtenerPorId(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "UsuarioService.obtenerPorId id=" + id, () ->
                        ApiResponse.ok("Usuario encontrado.", UsuarioResponse.of(obtenerUsuario(id)))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<UsuarioResponse>> crear(UsuarioCrearRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "UsuarioService.crear usuario=" + peticion.usuario(), () -> {
            if (usuarioRepository.existsByUsuario(peticion.usuario())) {
                throw new ConflictoException("Ya existe un usuario con el nombre '" + peticion.usuario() + "'.");
            }
            if (usuarioRepository.existsByEmail(peticion.email())) {
                throw new ConflictoException("Ya existe un usuario con el email '" + peticion.email() + "'.");
            }

            Usuario usuario = new Usuario();
            usuario.setUsuario(peticion.usuario().trim());
            usuario.setContrasena(passwordEncoder.encode(peticion.contrasena()));
            usuario.setNombre(peticion.nombre().trim());
            usuario.setApellido(peticion.apellido().trim());
            usuario.setEmail(peticion.email().trim());
            usuario.setTelefono(peticion.telefono());
            usuario.setActivo(peticion.activo() == null ? Boolean.TRUE : peticion.activo());
            Auditoria.alCrear(usuario, actor);
            usuarioRepository.save(usuario);

            return ApiResponse.ok("Usuario creado.", UsuarioResponse.of(usuario));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<UsuarioResponse>> actualizar(Long id, UsuarioActualizarRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "UsuarioService.actualizar id=" + id, () -> {
            Usuario usuario = obtenerUsuario(id);
            verificarNoProtegido(usuario);

            if (peticion.usuario() != null && !peticion.usuario().trim().equals(usuario.getUsuario())) {
                if (usuarioRepository.existsByUsuarioAndIdNot(peticion.usuario().trim(), id)) {
                    throw new ConflictoException("Ya existe otro usuario con el nombre '" + peticion.usuario() + "'.");
                }
                usuario.setUsuario(peticion.usuario().trim());
            }
            if (peticion.email() != null && !peticion.email().trim().equals(usuario.getEmail())) {
                if (usuarioRepository.existsByEmailAndIdNot(peticion.email().trim(), id)) {
                    throw new ConflictoException("Ya existe otro usuario con el email '" + peticion.email() + "'.");
                }
                usuario.setEmail(peticion.email().trim());
            }
            if (peticion.contrasena() != null) {
                usuario.setContrasena(passwordEncoder.encode(peticion.contrasena()));
            }
            if (peticion.nombre() != null) {
                usuario.setNombre(peticion.nombre().trim());
            }
            if (peticion.apellido() != null) {
                usuario.setApellido(peticion.apellido().trim());
            }
            if (peticion.telefono() != null) {
                usuario.setTelefono(peticion.telefono());
            }
            if (peticion.activo() != null) {
                usuario.setActivo(peticion.activo());
            }

            Auditoria.alActualizar(usuario, actor);
            usuarioRepository.save(usuario);
            return ApiResponse.ok("Usuario actualizado.", UsuarioResponse.of(usuario));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<Void>> eliminar(Long id) {
        return Mono.<Void>fromRunnable(() -> Trazas.medir(log, "UsuarioService.eliminar id=" + id, () -> {
                    Usuario usuario = obtenerUsuario(id);
                    verificarNoProtegido(usuario);
                    usuarioRepository.delete(usuario);
                    return null;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> ApiResponse.<Void>ok("Usuario eliminado.", null)));
    }

    public Mono<ApiResponse<UsuarioResponse>> cambiarActivo(Long id, CambiarEstadoRequest peticion) {
        Long actor = ActorActual.id();
        return Mono.fromCallable(() -> Trazas.medir(log, "UsuarioService.cambiarActivo id=" + id, () -> {
            Usuario usuario = obtenerUsuario(id);
            verificarNoProtegido(usuario);
            usuario.setActivo(peticion.activo());
            Auditoria.alActualizar(usuario, actor);
            usuarioRepository.save(usuario);

            String mensaje = peticion.activo() ? "Usuario activado." : "Usuario desactivado.";
            return ApiResponse.ok(mensaje, UsuarioResponse.of(usuario));
        })).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiResponse<List<RolResponse>>> rolesDeUsuario(Long id) {
        return Mono.fromCallable(() -> Trazas.medir(log, "UsuarioService.rolesDeUsuario id=" + id, () -> {
                    obtenerUsuario(id);
                    return usuarioRolRepository.rolesDeUsuario(id).stream().map(RolResponse::of).toList();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(lista -> ApiResponse.ok("Roles del usuario.", lista));
    }

    private Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + id + "."));
    }

    private void verificarNoProtegido(Usuario usuario) {
        if (Protecciones.esUsuarioProtegido(usuario.getUsuario())) {
            throw new OperacionNoPermitidaException(
                    "El usuario '" + usuario.getUsuario() + "' esta protegido: no se puede modificar ni eliminar.");
        }
    }
}
