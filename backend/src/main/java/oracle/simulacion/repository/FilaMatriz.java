package oracle.simulacion.repository;

import oracle.simulacion.model.TipoRuta;

/** Fila mínima de una ruta para armar la matriz cluster × chute. */
public interface FilaMatriz {

  Long getId();

  Long getClusterId();

  Long getChuteId();

  String getCodigo();

  TipoRuta getTipo();
}
