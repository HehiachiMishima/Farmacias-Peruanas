package oracle.simulacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Operarios asignados a un chute dentro de un archivo. */
@Entity
@Table(name = "parametros_chutes")
@Getter
@Setter
public class ParametroChute {

  @EmbeddedId
  private ParametroChuteId id;

  @Column(name = "operarios", nullable = false)
  private Integer operarios;
}
