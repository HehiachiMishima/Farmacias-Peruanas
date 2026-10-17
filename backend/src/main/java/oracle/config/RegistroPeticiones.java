package oracle.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * Traza cada peticion HTTP (metodo, ruta, estado y duracion en ms).
 * Se ejecuta antes de la cadena de seguridad para registrar tambien los
 * rechazos 401/403. No registra la query string para no volcar PII
 * (emails, telefonos) en los logs.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegistroPeticiones extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            long ms = (System.nanoTime() - inicio) / 1_000_000L;
            log.info("{} {} -> {} ({} ms)", peticion.getMethod(), peticion.getRequestURI(),
                    respuesta.getStatus(), ms);
        }
    }
}
