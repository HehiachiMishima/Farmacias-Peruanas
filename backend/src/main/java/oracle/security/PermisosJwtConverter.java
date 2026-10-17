package oracle.security;

import java.util.List;
import oracle.model.Usuario;
import oracle.repository.ResolucionRepository;
import oracle.repository.UsuarioRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Convierte el JWT en un Authentication con las autoridades del usuario,
 * resueltas por peticion contra la vista final. Esto respeta deny-overrides y
 * hace que desactivar un usuario o un rol surta efecto de inmediato, sin
 * reemitir el token. Si el usuario ya no existe o esta inactivo, rechaza.
 */
@Component
public class PermisosJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioRepository usuarioRepository;
    private final ResolucionRepository resolucionRepository;

    public PermisosJwtConverter(UsuarioRepository usuarioRepository, ResolucionRepository resolucionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.resolucionRepository = resolucionRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuario inexistente."));
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new BadCredentialsException("La cuenta esta desactivada.");
        }

        List<SimpleGrantedAuthority> autoridades = resolucionRepository.nombresPermitidos(usuarioId).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new JwtAuthenticationToken(jwt, autoridades, usuario.getUsuario());
    }
}
