package oracle.inbound.model;

import oracle.admin.model.Usuario;
import oracle.common.model.Auditable;

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
@Table(name = "data_proveedor")
@Getter
@Setter
public class DataProveedor extends Auditable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "proveedor", length = 100, nullable = false, unique = true)
  private String proveedor;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "creado_por", insertable = false, updatable = false)
  private Usuario creadoPorUsuario;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "actualizado_por", insertable = false, updatable = false)
  private Usuario actualizadoPorUsuario;
}
