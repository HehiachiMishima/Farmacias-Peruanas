package oracle.simulacion.repository;

import java.math.BigDecimal;

/** Agregado global de todas las rutas proyectadas. */
public interface ResumenGlobal {

  Long getChutes();

  BigDecimal getBultos();

  BigDecimal getUnidades();

  BigDecimal getLineas();
}
