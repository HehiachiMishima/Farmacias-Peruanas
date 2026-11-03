package oracle.inbound.controller;

import oracle.common.security.LimiteSolicitudes;
import oracle.common.security.LimiteUsoPanel;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Límite de uso del panel Inbound por usuario y minuto (429 + Retry-After al
 * pasarse). La exportación y la analítica (7 agregaciones) tienen cupos
 * propios más bajos que los listados.
 */
@Configuration
public class LimiteUsoInbound implements WebMvcConfigurer {

  static final LimiteUsoPanel.Cupos CUPOS = new LimiteUsoPanel.Cupos(
      300, // lecturas: listados paginados, búsqueda y catálogos
      60, // escrituras: altas, ediciones y bajas
      60, // analítica del dashboard (cacheada por filtro)
      10); // exportaciones completas

  private final LimiteSolicitudes limite;

  public LimiteUsoInbound(LimiteSolicitudes limite) {
    this.limite = limite;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new LimiteUsoPanel(limite, "inbound", CUPOS)).addPathPatterns("/api/inbound/**");
  }
}
