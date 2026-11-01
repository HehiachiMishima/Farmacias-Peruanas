package oracle.simulacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import oracle.common.model.Auditable;

/** Velocidad base (bultos por hora por operario) de un archivo. */
@Entity
@Table(name = "simulacion_config")
@Getter
@Setter
public class SimulacionConfig extends Auditable {

  @Id
  @Column(name = "archivo_id")
  private Long archivoId;

  @Column(name = "velocidad_base", precision = 10, scale = 2, nullable = false)
  private BigDecimal velocidadBase;

  /**
   * Tope global de operarios disponibles para el registro. El balanceo no
   * asigna más que este total; si falta, se mantiene el tope histórico por
   * chute (50) sin límite global.
   */
  @Column(name = "operarios_maximos")
  private Integer operariosMaximos;
}
