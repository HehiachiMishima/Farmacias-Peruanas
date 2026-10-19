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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_data_proveedor", insertable = false, updatable = false)
  private DataProveedor proveedor;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_data_incidencia_inbound", insertable = false, updatable = false)
  private DataIncidenciaInbound tipoIncidencia;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "creado_por", insertable = false, updatable = false)
  private Usuario creadoPorUsuario;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "actualizado_por", insertable = false, updatable = false)
  private Usuario actualizadoPorUsuario;
}
