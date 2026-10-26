package oracle.simulacion.repository;

import java.math.BigDecimal;

/** Agregado de rutas de un archivo para el listado de registros. */
public interface ResumenRutasArchivo {

  Long getArchivoId();

  Long getRutas();

  Long getChutes();

  BigDecimal getBultos();

  BigDecimal getUnidades();

  BigDecimal getLineas();
}
