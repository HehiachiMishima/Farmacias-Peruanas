package oracle.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Convierte el JWT en un Authentication con las autoridades del usuario.
 *
 * Las autoridades se resuelven contra la vista final (deny-overrides) pero a
 * traves de AutorizacionCache, de modo que no se ejecutan 2 consultas SQL en
 * cada peticion. Un usuario inexistente o inactivo se rechaza con 401.
 */
@Component
public class PermisosJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AutorizacionCache autorizacionCache;

    public PermisosJwtConverter(AutorizacionCache autorizacionCache) {
        this.autorizacionCache = autorizacionCache;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long usuarioId = parsearId(jwt.getSubject());
        AutorizacionCache.Autorizacion autorizacion = autorizacionCache.cargar(usuarioId);
        if (!autorizacion.activo()) {
            throw new BadCredentialsException("La cuenta esta desactivada.");
        }
        return new JwtAuthenticationToken(
                jwt, autorizacion.autoridades(), autorizacion.usuario());
    }

    private Long parsearId(String sub) {
        try {
            return Long.valueOf(sub);
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Token invalido.");
        }
    }
}
