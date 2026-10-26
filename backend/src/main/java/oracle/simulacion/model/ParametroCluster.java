package oracle.simulacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Tiempo límite (horas) de un cluster dentro de un archivo. */
@Entity
@Table(name = "parametros_clusters")
@Getter
@Setter
public class ParametroCluster {

  @EmbeddedId
  private ParametroClusterId id;

  @Column(name = "limite_horas", precision = 10, scale = 2, nullable = false)
  private BigDecimal limiteHoras;
}
