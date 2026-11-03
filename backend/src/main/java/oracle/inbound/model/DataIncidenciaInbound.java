package oracle.inbound.model;

import oracle.common.model.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "data_incidencia_inbound")
@Getter
@Setter
public class DataIncidenciaInbound extends Auditable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "incidencia_inbound", length = 100, nullable = false, unique = true)
  private String incidenciaInbound;
}
