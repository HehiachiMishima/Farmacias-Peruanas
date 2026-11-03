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
@Table(name = "incidencias_inbound")
@Getter
@Setter
public class IncidenciaInbound extends Auditable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "orden_compra", length = 30, nullable = false)
  private String ordenCompra;

  @Column(name = "id_data_proveedor", nullable = false)
  private Long idDataProveedor;

  @Column(name = "id_data_incidencia_inbound", nullable = false)
  private Long idDataIncidenciaInbound;

  @Column(name = "sku_producto", length = 30, nullable = false)
  private String skuProducto;

  @Column(name = "n_unidades", nullable = false)
  private Integer nUnidades;
}
