package oracle.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import oracle.admin.model.Usuario;
import oracle.admin.repository.ResolucionRepository;
import oracle.admin.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class AutorizacionCacheTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ResolucionRepository resolucionRepository;

    private AutorizacionCache cache(boolean habilitado) {
        return new AutorizacionCache(usuarioRepository, resolucionRepository, habilitado, 60_000L, 100L);
    }

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setUsuario("ana");
        usuario.setActivo(Boolean.TRUE);
        return usuario;
    }

    @Test
    void reutilizaLaEntradaEnLaSegundaConsulta() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioActivo()));
        when(resolucionRepository.nombresPermitidos(1L)).thenReturn(List.of("VER_USUARIOS", "VER_ROLES"));

        AutorizacionCache cache = cache(true);
        AutorizacionCache.Autorizacion primera = cache.cargar(1L);
        AutorizacionCache.Autorizacion segunda = cache.cargar(1L);

        assertThat(primera.autoridades())
                .extracting(SimpleGrantedAuthority::getAuthority)
                .containsExactly("VER_USUARIOS", "VER_ROLES");
        assertThat(segunda.usuario()).isEqualTo("ana");
        verify(usuarioRepository, times(1)).findById(1L);
        verify(resolucionRepository, times(1)).nombresPermitidos(1L);
    }

    @Test
    void invalidarFuerzaUnaNuevaConsulta() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioActivo()));
        when(resolucionRepository.nombresPermitidos(1L)).thenReturn(List.of());

        AutorizacionCache cache = cache(true);
        cache.cargar(1L);
        cache.invalidar(1L);
        cache.cargar(1L);

        verify(usuarioRepository, times(2)).findById(1L);
    }

    @Test
    void deshabilitadaConsultaSiempre() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioActivo()));
        when(resolucionRepository.nombresPermitidos(1L)).thenReturn(List.of());

        AutorizacionCache cache = cache(false);
        cache.cargar(1L);
        cache.cargar(1L);

        verify(usuarioRepository, times(2)).findById(1L);
    }

    @Test
    void usuarioInexistenteLanzaCredencialesInvalidas() {
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cache(true).cargar(9L))
                .isInstanceOf(BadCredentialsException.class);
    }
}
