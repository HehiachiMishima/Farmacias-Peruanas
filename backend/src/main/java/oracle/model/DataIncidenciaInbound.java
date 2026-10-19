package oracle.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "creado_por", insertable = false, updatable = false)
  private Usuario creadoPorUsuario;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "actualizado_por", insertable = false, updatable = false)
  private Usuario actualizadoPorUsuario;
}
